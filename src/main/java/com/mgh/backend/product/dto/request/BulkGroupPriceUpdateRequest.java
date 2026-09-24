package com.mgh.backend.product.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkGroupPriceUpdateRequest {

    @NotNull(message = "Selling price is required")
    @PositiveOrZero(message = "Selling price must be positive or zero")
    private BigDecimal sellingPrice;
}
