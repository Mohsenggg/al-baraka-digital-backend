package com.mgh.backend.product.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddBarcodeRequest {

    @NotBlank
    private String barcode;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal sellingPrice;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal buyingPrice;

    @NotNull
    @PositiveOrZero
    private Integer stock;

    @JsonProperty("isDefault")
    private boolean isDefault;
}
