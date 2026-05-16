package com.mgh.backend.cashier.controller;


import com.mgh.backend.cashier.dto.CashierResponseDto;
import com.mgh.backend.cashier.dto.CreateCashierRequest;
import com.mgh.backend.cashier.dto.UpdateCashierRequest;
import com.mgh.backend.cashier.service.CashierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cashier")
@RequiredArgsConstructor
public class CashierController {

    private final CashierService cashierService;

    @PostMapping
    public ResponseEntity<CashierResponseDto> createCashier(
            @Valid @RequestBody CreateCashierRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cashierService.createCashier(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CashierResponseDto> getCashier(@PathVariable Long id) {
        return ResponseEntity.ok(cashierService.getCashier(id));
    }

    @GetMapping
    public ResponseEntity<List<CashierResponseDto>> getCashiers() {
        return ResponseEntity.ok(cashierService.getCashiers());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CashierResponseDto> updateCashier(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCashierRequest request
    ) {
        return ResponseEntity.ok(cashierService.updateCashier(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCashier(@PathVariable Long id) {
        cashierService.deleteCashier(id);
        return ResponseEntity.noContent().build();
    }
}