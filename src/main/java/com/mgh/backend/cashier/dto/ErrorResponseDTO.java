package com.mgh.backend.cashier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@AllArgsConstructor
public class ErrorResponseDTO {

    private String message;

    private Integer status;

    private LocalDateTime timestamp;

    private Map<String, String> errors;

    public ErrorResponseDTO(String message, Integer status) {
        this.message = message;
        this.status = status;
        this.timestamp = LocalDateTime.now();
        this.errors = null;
    }
}