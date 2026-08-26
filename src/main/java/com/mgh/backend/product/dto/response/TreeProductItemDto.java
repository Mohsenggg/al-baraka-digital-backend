package com.mgh.backend.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreeProductItemDto {
    private Long id;
    private String sku;
    private String name;
    private Long productGroupId;
    private BigDecimal sellingPrice;
    private BigDecimal buyingPrice;
    private BigDecimal price0;
    private BigDecimal price1;
    private BigDecimal price2;
    private BigDecimal price3;
    private BigDecimal price4;
    private String vendorCode;
    private Double stock;
    private Double minStockLevel;
    private Double maxStockLevel;
    private Double minStock; // frontend alias compatibility
    private Double maxStock; // frontend alias compatibility
    private String stockStatus;
    private String type;
    private String status;
}
