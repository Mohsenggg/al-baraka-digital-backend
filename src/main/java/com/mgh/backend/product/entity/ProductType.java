package com.mgh.backend.product.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductType {
    INVENTORY("inventory"),
    SERVICE("service"),
    BUNDLE("bundle"),
    RAW("raw");

    private final String value;

    ProductType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ProductType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ProductType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown product type: " + value);
    }
}
