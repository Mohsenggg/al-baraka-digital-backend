package com.mgh.backend.product.controller;

import com.mgh.backend.product.dto.request.CreateReferenceDataRequest;
import com.mgh.backend.product.dto.response.ReferenceItemDto;
import com.mgh.backend.product.service.ProductLookupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lookups")
@RequiredArgsConstructor
public class ProductLookupController {

    private final ProductLookupService productLookupService;

    @GetMapping("/categories")
    public ResponseEntity<List<ReferenceItemDto>> getCategories() {
        return ResponseEntity.ok(productLookupService.getCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<ReferenceItemDto> createCategory(@Valid @RequestBody CreateReferenceDataRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productLookupService.createCategory(request));
    }

    @GetMapping("/manufacturers")
    public ResponseEntity<List<ReferenceItemDto>> getManufacturers() {
        return ResponseEntity.ok(productLookupService.getManufacturers());
    }

    @PostMapping("/manufacturers")
    public ResponseEntity<ReferenceItemDto> createManufacturer(@Valid @RequestBody CreateReferenceDataRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productLookupService.createManufacturer(request));
    }

    @GetMapping("/suppliers")
    public ResponseEntity<List<ReferenceItemDto>> getSuppliers() {
        return ResponseEntity.ok(productLookupService.getSuppliers());
    }

    @PostMapping("/suppliers")
    public ResponseEntity<ReferenceItemDto> createSupplier(@Valid @RequestBody CreateReferenceDataRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productLookupService.createSupplier(request));
    }

    @GetMapping("/attributes")
    public ResponseEntity<List<ReferenceItemDto>> getAttributes() {
        return ResponseEntity.ok(productLookupService.getAttributes());
    }

    @PostMapping("/attributes")
    public ResponseEntity<ReferenceItemDto> createAttribute(@Valid @RequestBody CreateReferenceDataRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productLookupService.createAttribute(request));
    }
}
