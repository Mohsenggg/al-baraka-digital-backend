
// receipt.dto.ReceiptRequestDto.java
package com.mgh.backend.cashier.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class ReceiptRequestDto {
    private String customerName;          // optional
    private Long customerId;              // optional
    @NotNull
    private Long cashierId;               // who is creating/updating
    @NotEmpty
    private List<ReceiptItemRequest> items;
    private String paymentMethod;         // defaults to CASH if null
    private String receiptType;
    private Integer totalQuantity;// defaults to SELL
}