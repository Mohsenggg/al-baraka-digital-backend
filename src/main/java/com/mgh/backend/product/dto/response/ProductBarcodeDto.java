package com.mgh.backend.product.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class ProductBarcodeDto {

    private Long id;
    private String barcode;
    private BigDecimal sellingPrice;
    private BigDecimal buyingPrice;
    private Double stock;

    @JsonProperty("default")
    private boolean defaultBarcode;
}
