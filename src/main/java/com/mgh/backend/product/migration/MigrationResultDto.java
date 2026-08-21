package com.mgh.backend.product.migration;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MigrationResultDto {
    private boolean success;
    private String message;
    private int categoriesCreated;
    private int brandsCreated;
    private int productGroupsCreated;
    private int productsCreated;
    private int totalRecordsProcessed;
}
