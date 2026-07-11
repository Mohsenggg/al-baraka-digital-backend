package com.mgh.backend.product.dto;

import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.StockStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> withFilters(ProductSearchFilter filter) {
        return (root, query, cb) -> {
            if (query != null && Product.class.equals(query.getResultType())) {
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (StringUtils.hasText(filter.getQuery())) {
                String pattern = "%" + filter.getQuery().toLowerCase() + "%";
                Join<Object, Object> barcodes = root.join("barcodes", JoinType.LEFT);
                Join<Object, Object> attributeValues = root.join("attributeValues", JoinType.LEFT);

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(barcodes.get("barcode")), pattern),
                        cb.like(cb.lower(attributeValues.get("value")), pattern)
                ));
            }

            if (StringUtils.hasText(filter.getCategory())) {
                Join<Object, Object> category = root.join("category", JoinType.LEFT);
                String categoryValue = filter.getCategory().toLowerCase();
                List<Predicate> categoryPredicates = new ArrayList<>();
                categoryPredicates.add(cb.equal(cb.lower(category.get("slug")), categoryValue));
                categoryPredicates.add(cb.equal(cb.lower(category.get("name")), categoryValue));
                try {
                    Long categoryId = Long.parseLong(filter.getCategory());
                    categoryPredicates.add(cb.equal(category.get("id"), categoryId));
                } catch (NumberFormatException ignored) {
                    // not a numeric category id
                }
                predicates.add(cb.or(categoryPredicates.toArray(new Predicate[0])));
            }

            if (filter.getType() != null) {
                predicates.add(cb.equal(root.get("type"), filter.getType()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        filter.getDateFrom().atStartOfDay()
                ));
            }

            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        filter.getDateTo().atTime(LocalTime.MAX)
                ));
            }

            if (filter.getPriceMin() != null || filter.getPriceMax() != null) {
                Subquery<BigDecimal> maxPrice = query.subquery(BigDecimal.class);
                var barcodeRoot = maxPrice.from(com.mgh.backend.product.entity.ProductBarcode.class);
                maxPrice.select(cb.max(barcodeRoot.get("sellingPrice")));
                maxPrice.where(
                        cb.equal(barcodeRoot.get("product"), root),
                        cb.isNull(barcodeRoot.get("deletedAt"))
                );

                if (filter.getPriceMin() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(maxPrice, filter.getPriceMin()));
                }
                if (filter.getPriceMax() != null) {
                    predicates.add(cb.lessThanOrEqualTo(maxPrice, filter.getPriceMax()));
                }
            }

            if (filter.getStockMin() != null || filter.getStockMax() != null || filter.getStockStatus() != null) {
                Subquery<Integer> totalStock = query.subquery(Integer.class);
                var barcodeRoot = totalStock.from(com.mgh.backend.product.entity.ProductBarcode.class);
                totalStock.select(cb.coalesce(cb.sum(barcodeRoot.get("stock")), 0));
                totalStock.where(
                        cb.equal(barcodeRoot.get("product"), root),
                        cb.isNull(barcodeRoot.get("deletedAt"))
                );

                if (filter.getStockMin() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(totalStock, filter.getStockMin()));
                }
                if (filter.getStockMax() != null) {
                    predicates.add(cb.lessThanOrEqualTo(totalStock, filter.getStockMax()));
                }

                if (filter.getStockStatus() != null) {
                    predicates.add(buildStockStatusPredicate(cb, root, totalStock, filter.getStockStatus()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate buildStockStatusPredicate(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<Product> root,
            Subquery<Integer> totalStock,
            StockStatus stockStatus
    ) {
        var minLevel = cb.coalesce(root.get("minStockLevel"), 0);
        var zero = cb.literal(0);
        var lowThreshold = cb.prod(minLevel, cb.literal(1.5d));

        return switch (stockStatus) {
            case OUTOFSTOCK -> cb.equal(totalStock, zero);
            case CRITICAL -> cb.and(
                    cb.greaterThan(totalStock, zero),
                    cb.lessThanOrEqualTo(totalStock, minLevel)
            );
            case LOW -> cb.and(
                    cb.greaterThan(totalStock, minLevel),
                    cb.lessThanOrEqualTo(totalStock, lowThreshold)
            );
            case HEALTHY -> cb.or(
                    cb.equal(minLevel, zero),
                    cb.greaterThan(totalStock, lowThreshold)
            );
        };
    }
}