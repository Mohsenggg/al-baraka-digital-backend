package com.mgh.backend.cashier.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RefillValidateRequest {
    @NotBlank
    private String childBarcode;
    @NotNull
    private Long parentProductId;
    @NotNull
    @Min(1)
    private Double requestedChildQuantity;
}
