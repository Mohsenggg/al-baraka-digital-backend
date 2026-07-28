package com.mgh.backend.cashier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ReceiptItemRequest {

    private Long id;

    @NotBlank(message = "Product code is required")
    private String productCode;

    private String productName;

    @DecimalMin(value = "0.00", message = "Price must be greater than or equal to 0")
    private BigDecimal price;

    @DecimalMin(value = "0.001", message = "Quantity must be greater than 0")
    private Double quantity;

    private BigDecimal total;

    private Double remainingStock;
}