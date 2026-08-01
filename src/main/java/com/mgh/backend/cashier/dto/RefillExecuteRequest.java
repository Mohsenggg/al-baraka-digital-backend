package com.mgh.backend.cashier.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class RefillExecuteRequest {
    @NotBlank
    private String childBarcode;
    
    @NotNull
    private Long parentProductId;
    
    @NotNull
    @Min(1)
    private Double requestedChildQuantity;
    
    private boolean acceptPricingChange;
    
    // Expected prices to ensure validation hasn't gone stale
    private BigDecimal expectedNewBuyingPrice;
    private BigDecimal expectedProposedSellingPrice;
}
