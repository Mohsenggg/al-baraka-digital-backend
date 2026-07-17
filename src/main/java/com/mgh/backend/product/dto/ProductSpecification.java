package com.mgh.backend.product.dto;

import com.mgh.backend.product.entity.Product;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    public static Specification<Product> withFilters(ProductSearchFilter filter, Sort sort) {
        return (root, query, cb) -> {
            // Eagerly fetch category and manufacturer for list
            if (Long.class != query.getResultType()) { // Avoid fetch on count query
                root.fetch("category", JoinType.LEFT);
                root.fetch("manufacturer", JoinType.LEFT);
                root.fetch("barcodes", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isNull(root.get("deletedAt")));

            if (filter.getQuery() != null && !filter.getQuery().trim().isEmpty()) {
                String pattern = "%" + filter.getQuery().trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                Predicate barcodeMatch = cb.like(cb.lower(root.get("barcode")), pattern);
                predicates.add(cb.or(nameMatch, barcodeMatch));
            }

            if (filter.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.getCategoryId()));
            }

            if (filter.getManufacturerId() != null) {
                predicates.add(cb.equal(root.get("manufacturer").get("id"), filter.getManufacturerId()));
            }

            if (filter.getSupplierId() != null) {
                predicates.add(cb.equal(root.join("suppliers", JoinType.INNER).get("id"), filter.getSupplierId()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            query.distinct(true);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
