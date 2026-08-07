package com.mgh.backend.cashier.printing.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReceiptLayoutConfig {
    @Builder.Default
    private boolean showLogo = true;
    @Builder.Default
    private boolean showCustomerPhone = true;
    @Builder.Default
    private boolean showCashier = true;
    @Builder.Default
    private boolean showTaxDetails = true;
    @Builder.Default
    private String footerText = "Thank you for your visit!";
}
