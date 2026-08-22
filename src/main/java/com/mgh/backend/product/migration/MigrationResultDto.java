package com.mgh.backend.product.migration;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MigrationResultDto {
    private boolean success;
    private int totalRecords;
    private int successfulRecords;
    private int alreadyExisting;
    private int failedRecords;
    private List<MigrationErrorDto.ValidationError> failures;
}
