package com.mgh.backend.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class ProductListItemDto {

    private Long id;
    private String name;
    private String barcode;
    private String category;
    private String manufacturer;
    private BigDecimal sellingPrice;
    private Integer stock;
    private String status;
}
