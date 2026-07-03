package com.mgh.backend.cashier.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ReceiptRequestDto {
    private String customerName;
    private Long customerId;
    @NotNull(message = "Cashier ID is required")
    private Long cashierId;
    @NotEmpty(message = "Receipt must contain at least one item")
    @Valid
    private List<ReceiptItemRequest> items;
    private String paymentMethod;
    private String receiptType;
    private Integer totalQuantity;
    private BigDecimal tax;
    private BigDecimal discount;
}