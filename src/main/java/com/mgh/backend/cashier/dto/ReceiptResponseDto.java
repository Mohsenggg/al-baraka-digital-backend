package com.mgh.backend.cashier.dto;

import com.mgh.backend.cashier.entity.PaymentMethod;
import com.mgh.backend.cashier.entity.ReceiptStatus;
import com.mgh.backend.cashier.entity.ReceiptType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class ReceiptResponseDto {

    private Long id;
    private String receiptNumber;
    private LocalDateTime receiptDate;          // ← was missing
    private PaymentMethod paymentMethod;
    private ReceiptType receiptType;
    private String customerName;
    private Long cashierId;
    private String cashierName;
    private BigDecimal totalAmount;
    private Integer totalQuantity;
    private Integer totalItems;
    private ReceiptStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ReceiptItemResponse> items;
}