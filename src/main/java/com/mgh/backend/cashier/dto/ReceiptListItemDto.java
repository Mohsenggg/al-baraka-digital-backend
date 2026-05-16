package com.mgh.backend.cashier.dto;

import com.mgh.backend.cashier.entity.PaymentMethod;
import com.mgh.backend.cashier.entity.ReceiptStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ReceiptListItemDto {

    private Long id;
    private String receiptCode;
    private LocalDateTime receiptDate;
    private BigDecimal totalAmount;
    private Integer totalItems;
    private String customerName;
    private ReceiptStatus status;
    private PaymentMethod paymentMethod;
    private String cashierName;
}