package com.mgh.backend.product.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationErrorDto {
    private boolean success;
    private String message;
    private List<ValidationError> errors;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ValidationError {
        private String itemCode;
        private String reason;
    }
}
