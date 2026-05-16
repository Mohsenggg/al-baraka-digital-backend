// receipt.controller.ReceiptController.java
package com.mgh.backend.cashier.controller;

import com.mgh.backend.cashier.dto.*;
import com.mgh.backend.cashier.entity.PaymentMethod;
import com.mgh.backend.cashier.entity.ReceiptStatus;
import com.mgh.backend.cashier.service.ReceiptService;
import com.mgh.backend.cashier.service.impl.ReceiptServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptServiceImpl receiptService;

    @PostMapping
    public ResponseEntity<ReceiptResponseDto> createReceipt(@Valid @RequestBody ReceiptRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(receiptService.createReceipt(request));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<ReceiptResponseDto> revokeReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(receiptService.revokeReceipt(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReceiptResponseDto> getReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(receiptService.getReceipt(id));
    }

    @GetMapping
    public ResponseEntity<List<ReceiptResponseDto>> getReceipts() {
        return ResponseEntity.ok(receiptService.getReceipts());
    }

    @GetMapping("/filter")
    public ResponseEntity<PageResponseDto<ReceiptListItemDto>> searchReceipts(
            @RequestParam(required = false) String code,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            @RequestParam(required = false) BigDecimal totalMin,
            @RequestParam(required = false) BigDecimal totalMax,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) ReceiptStatus status,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "receiptDate",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        ReceiptSearchFilter filter = new ReceiptSearchFilter();
        filter.setCode(code);
        filter.setFromDate(fromDate);
        filter.setToDate(toDate);
        filter.setTotalMin(totalMin);
        filter.setTotalMax(totalMax);
        filter.setCustomerName(customerName);
        filter.setStatus(status);
        filter.setPaymentMethod(paymentMethod);

        return ResponseEntity.ok(receiptService.searchReceipts(filter, pageable));
    }



    @GetMapping("/navigation-window")
    public ResponseEntity<ReceiptNavigationWindowResponse> getNavigationWindow(
            @RequestParam(required = false) Long centerReceiptId,
            @RequestParam(defaultValue = "0") int before,
            @RequestParam(defaultValue = "10") int after
    ) {
        return ResponseEntity.ok(
                receiptService.getNavigationWindow(centerReceiptId, before, after)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReceiptResponseDto> updateReceipt(
            @PathVariable Long id,
            @Valid @RequestBody ReceiptRequestDto request) {
        return ResponseEntity.ok(receiptService.updateReceipt(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReceipt(@PathVariable Long id) {
        receiptService.deleteReceipt(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/draft")
    public ResponseEntity<ReceiptResponseDto> draftReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(receiptService.draftReceipt(id));
    }
}