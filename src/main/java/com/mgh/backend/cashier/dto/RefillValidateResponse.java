package com.mgh.backend.cashier.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RefillValidateResponse {
    private boolean isValid;
    private boolean pricingChangeRequired;
    
    private BigDecimal currentBuyingPrice;
    private BigDecimal newBuyingPrice;
    
    private BigDecimal currentSellingPrice;
    private BigDecimal proposedSellingPrice;
}
