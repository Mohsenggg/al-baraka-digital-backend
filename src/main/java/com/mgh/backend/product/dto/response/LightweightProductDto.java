package com.mgh.backend.product.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LightweightProductDto {
    private Long id;
    private String barcode;
    private String name;
    private BigDecimal sellingPrice;
    private BigDecimal buyingPrice;
    private Double stock;
    private List<ProductDto.RefillOptionDto> refillOptions;

    public LightweightProductDto() {
        this.refillOptions = new ArrayList<>();
    }

    /** Used by JPQL constructor expression in ProductRepository */
    public LightweightProductDto(Long id, String barcode, String name, BigDecimal sellingPrice, BigDecimal buyingPrice, Double stock) {
        this.id = id;
        this.barcode = barcode;
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.buyingPrice = buyingPrice;
        this.stock = stock;
        this.refillOptions = new ArrayList<>();
    }
}
