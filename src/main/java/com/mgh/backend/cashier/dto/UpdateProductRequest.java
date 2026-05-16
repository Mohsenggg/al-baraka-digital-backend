package com.mgh.backend.cashier.dto;

// product.dto.UpdateProductRequest.java

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateProductRequest {
    @Size(max = 100)
    private String code;

    @Size(max = 255)
    private String name;

    @DecimalMin("0.0")
    private BigDecimal price;

    @PositiveOrZero
    private Integer stock;
}