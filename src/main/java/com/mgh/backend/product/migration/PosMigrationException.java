package com.mgh.backend.product.migration;

import java.util.List;

public class PosMigrationException extends RuntimeException {

    private final List<MigrationErrorDto.ValidationError> errors;

    public PosMigrationException(String message, List<MigrationErrorDto.ValidationError> errors) {
        super(message);
        this.errors = errors;
    }

    public List<MigrationErrorDto.ValidationError> getErrors() {
        return errors;
    }
}
