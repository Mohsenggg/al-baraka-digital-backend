package com.mgh.backend.product.migration;

import java.util.List;

public interface PosMigrationService {
    MigrationResultDto importPosData(List<PosDataItemDto> items);
}
