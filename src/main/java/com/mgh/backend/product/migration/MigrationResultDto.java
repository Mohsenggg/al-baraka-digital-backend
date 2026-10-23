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
    private int createdRecords;
    private int disambiguatedRecords;
    private int mergedRecords;
    private int alreadyExisting;
    private int failedRecords;
    private List<MigrationErrorDto.ValidationError> failures;
}
