package com.mgh.backend.cashier.exception;

public class DuplicateReceiptException extends RuntimeException {

    public DuplicateReceiptException(String message) {
        super(message);
    }
}
