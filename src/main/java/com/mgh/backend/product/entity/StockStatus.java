package com.mgh.backend.product.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StockStatus {
    HEALTHY("healthy"),
    LOW("low"),
    CRITICAL("critical"),
    OUTOFSTOCK("outofstock");

    private final String value;

    StockStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static StockStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (StockStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown stock status: " + value);
    }
}
