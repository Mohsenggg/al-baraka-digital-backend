package com.mgh.backend.product.dto;

import com.mgh.backend.cashier.exception.BadRequestException;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.StockStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProductSpecification {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name",
            "code",
            "createdAt",
            "maxSellingPrice",
            "totalStock",
            "type",
            "status"
    );

    private ProductSpecification() {
    }

    public static Specification<Product> withFilters(ProductSearchFilter filter, Sort sort) {
        validateSort(sort);

        return (root, query, cb) -> {
            if (query != null && Product.class.equals(query.getResultType())) {
                query.distinct(true);
                applySort(root, query, cb, sort);
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
                        cb.and(
                                cb.isNull(barcodes.get("deletedAt")),
                                cb.like(cb.lower(barcodes.get("barcode")), pattern)
                        ),
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
                Subquery<BigDecimal> maxPrice = maxSellingPriceSubquery(root, query, cb);

                if (filter.getPriceMin() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(maxPrice, filter.getPriceMin()));
                }
                if (filter.getPriceMax() != null) {
                    predicates.add(cb.lessThanOrEqualTo(maxPrice, filter.getPriceMax()));
                }
            }

            if (filter.getStockMin() != null || filter.getStockMax() != null || filter.getStockStatus() != null) {
                Subquery<Integer> totalStock = totalStockSubquery(root, query, cb);

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

    private static void validateSort(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return;
        }

        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException(
                        "Unsupported sort field: " + order.getProperty()
                                + ". Allowed fields: " + ALLOWED_SORT_FIELDS.stream().sorted().collect(Collectors.joining(", "))
                );
            }
        }
    }

    private static void applySort(
            Root<Product> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Sort sort
    ) {
        List<Order> orders = new ArrayList<>();

        if (sort == null || sort.isUnsorted()) {
            orders.add(cb.desc(root.get("createdAt")));
            query.orderBy(orders);
            return;
        }

        for (Sort.Order order : sort) {
            Expression<?> sortExpression = switch (order.getProperty()) {
                case "name", "code", "createdAt", "type", "status" -> root.get(order.getProperty());
                case "maxSellingPrice" -> maxSellingPriceSubquery(root, query, cb);
                case "totalStock" -> totalStockSubquery(root, query, cb);
                default -> throw new BadRequestException("Unsupported sort field: " + order.getProperty());
            };
            orders.add(order.isAscending() ? cb.asc(sortExpression) : cb.desc(sortExpression));
        }

        query.orderBy(orders);
    }

    private static Subquery<BigDecimal> maxSellingPriceSubquery(
            Root<Product> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb
    ) {
        Subquery<BigDecimal> maxPrice = query.subquery(BigDecimal.class);
        var barcodeRoot = maxPrice.from(ProductBarcode.class);
        maxPrice.select(cb.coalesce(cb.max(barcodeRoot.get("sellingPrice")), BigDecimal.ZERO));
        maxPrice.where(
                cb.equal(barcodeRoot.get("product"), root),
                cb.isNull(barcodeRoot.get("deletedAt"))
        );
        return maxPrice;
    }

    private static Subquery<Integer> totalStockSubquery(
            Root<Product> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb
    ) {
        Subquery<Integer> totalStock = query.subquery(Integer.class);
        var barcodeRoot = totalStock.from(ProductBarcode.class);
        totalStock.select(cb.coalesce(cb.sum(barcodeRoot.get("stock")), 0));
        totalStock.where(
                cb.equal(barcodeRoot.get("product"), root),
                cb.isNull(barcodeRoot.get("deletedAt"))
        );
        return totalStock;
    }

    private static Predicate buildStockStatusPredicate(
            CriteriaBuilder cb,
            Root<Product> root,
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
                    cb.equal(minLevel, 0),
                    cb.greaterThan(totalStock, lowThreshold)
            );
        };
    }
}
