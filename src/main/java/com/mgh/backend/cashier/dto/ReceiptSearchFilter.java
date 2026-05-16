package com.mgh.backend.cashier.dto;

import com.mgh.backend.cashier.entity.PaymentMethod;
import com.mgh.backend.cashier.entity.ReceiptStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ReceiptSearchFilter {

    private String code;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal totalMin;
    private BigDecimal totalMax;
    private String customerName;
    private ReceiptStatus status;
    private PaymentMethod paymentMethod;
}