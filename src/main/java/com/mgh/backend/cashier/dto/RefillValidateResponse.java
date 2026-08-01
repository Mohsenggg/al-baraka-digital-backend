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
    
    /** Preserved markup % (e.g. 25.00 means 25%). Used for display and stale-check on execute. */
    private BigDecimal currentMarkupPercentage;
}
