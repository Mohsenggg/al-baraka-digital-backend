package com.mgh.backend.product.dto;

import com.mgh.backend.product.entity.ProductStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductSearchFilter {

    private String query;
    private Long categoryId;
    private Long manufacturerId;
    private Long supplierId;
    private ProductStatus status;
}
