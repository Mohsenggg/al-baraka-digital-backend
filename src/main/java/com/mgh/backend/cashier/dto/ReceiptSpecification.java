package com.mgh.backend.cashier.dto;


import com.mgh.backend.cashier.dto.ReceiptSearchFilter;
import com.mgh.backend.cashier.entity.Receipt;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ReceiptSpecification {

    private ReceiptSpecification() {
    }

    public static Specification<Receipt> withFilters(ReceiptSearchFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));

            if (StringUtils.hasText(filter.getCode())) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("receiptNumber")),
                                "%" + filter.getCode().toLowerCase() + "%"
                        )
                );
            }

            if (filter.getFromDate() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("receiptDate"),
                                filter.getFromDate().atStartOfDay()
                        )
                );
            }

            if (filter.getToDate() != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("receiptDate"),
                                filter.getToDate().atTime(LocalTime.MAX)
                        )
                );
            }

            if (filter.getTotalMin() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("totalAmount"),
                                filter.getTotalMin()
                        )
                );
            }

            if (filter.getTotalMax() != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("totalAmount"),
                                filter.getTotalMax()
                        )
                );
            }

            if (StringUtils.hasText(filter.getCustomerName())) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("customerName")),
                                "%" + filter.getCustomerName().toLowerCase() + "%"
                        )
                );
            }

            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getPaymentMethod() != null) {
                predicates.add(criteriaBuilder.equal(root.get("paymentMethod"), filter.getPaymentMethod()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}