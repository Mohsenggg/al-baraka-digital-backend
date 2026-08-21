package com.mgh.backend.product.migration;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/migration")
@RequiredArgsConstructor
public class PosMigrationController {

    private final PosMigrationService posMigrationService;

    @PostMapping("/pos-data")
    public ResponseEntity<MigrationResultDto> importPosData(@RequestBody List<PosDataItemDto> items) {
        MigrationResultDto result = posMigrationService.importPosData(items);
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(PosMigrationException.class)
    public ResponseEntity<MigrationErrorDto> handleMigrationException(PosMigrationException ex) {
        MigrationErrorDto error = MigrationErrorDto.builder()
                .success(false)
                .message("POS data migration failed")
                .errors(ex.getErrors())
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }
}
