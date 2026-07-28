package com.mgh.backend.cashier.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ReceiptItemResponse {
    private String productCode;
    private String productName;
    private Double quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private Double remainingStock;
    private Double currentRemainingStock;
}