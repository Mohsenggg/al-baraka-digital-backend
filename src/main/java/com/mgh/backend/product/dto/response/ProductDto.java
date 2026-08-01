package com.mgh.backend.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class ProductDto {

    private Long id;
    private String barcode;
    private String name;
    private BigDecimal sellingPrice;
    private BigDecimal buyingPrice;
    private Double stock;
    
    @Builder.Default
    private java.util.List<RefillOptionDto> refillOptions = new java.util.ArrayList<>();

    @Getter
    @Setter
    @Builder
    public static class RefillOptionDto {
        private Long parentProductId;
        private String parentProductName;
        private Integer parentQuantity;
        private Integer childQuantity;
        private Double parentStock;
        private boolean isDefault;
    }
}
