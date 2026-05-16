package com.mgh.backend.cashier.exception;

public class UnauthorizedInvoiceOperationException extends RuntimeException {

    public UnauthorizedInvoiceOperationException(String message) {
        super(message);
    }
}