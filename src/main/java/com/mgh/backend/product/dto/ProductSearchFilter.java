package com.mgh.backend.product.dto;

import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import com.mgh.backend.product.entity.StockStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ProductSearchFilter {

    private String query;
    private String category;
    private ProductType type;
    private StockStatus stockStatus;
    private ProductStatus status;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private Integer stockMin;
    private Integer stockMax;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
