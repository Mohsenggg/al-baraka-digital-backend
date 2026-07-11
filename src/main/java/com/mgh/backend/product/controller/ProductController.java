package com.mgh.backend.product.controller;

import com.mgh.backend.cashier.dto.PageResponseDto;
import com.mgh.backend.product.dto.ProductSearchFilter;
import com.mgh.backend.product.dto.request.AddBarcodeRequest;
import com.mgh.backend.product.dto.request.CreateProductRequest;
import com.mgh.backend.product.dto.request.CreateReferenceDataRequest;
import com.mgh.backend.product.dto.request.ProductManageSaveRequest;
import com.mgh.backend.product.dto.request.ProductStatusUpdateRequest;
import com.mgh.backend.product.dto.request.UpdateProductRequest;
import com.mgh.backend.product.dto.response.ProductBarcodeDto;
import com.mgh.backend.product.dto.response.ProductDto;
import com.mgh.backend.product.dto.response.ProductListItemDto;
import com.mgh.backend.product.dto.response.ProductManageDetailDto;
import com.mgh.backend.product.dto.response.ProductReferenceDataDto;
import com.mgh.backend.product.dto.response.ProductStatusUpdateResponse;
import com.mgh.backend.product.dto.response.ReferenceItemDto;
import com.mgh.backend.product.dto.response.StockSummaryDto;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import com.mgh.backend.product.entity.StockStatus;
import com.mgh.backend.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/all-products")
    public ResponseEntity<List<ProductDto>> getAllProductsForCashier() {
        return ResponseEntity.ok(productService.getAllProductsForCashier());
    }

    @GetMapping("/reference-data")
    public ResponseEntity<ProductReferenceDataDto> getReferenceData() {
        return ResponseEntity.ok(productService.getReferenceData());
    }

    @PostMapping("/reference-data/{type}")
    public ResponseEntity<ReferenceItemDto> createReferenceData(
            @PathVariable String type,
            @Valid @RequestBody CreateReferenceDataRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createReferenceData(type, request));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductListItemDto>> quickSearch(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(productService.quickSearch(query, limit));
    }

    @GetMapping("/filter")
    public ResponseEntity<PageResponseDto<ProductListItemDto>> filterProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) StockStatus stockStatus,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Integer stockMin,
            @RequestParam(required = false) Integer stockMax,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(productService.listProducts(
                buildFilter(query, category, type, stockStatus, status, priceMin, priceMax, stockMin, stockMax, dateFrom, dateTo),
                pageable
        ));
    }

    @GetMapping
    public ResponseEntity<PageResponseDto<ProductListItemDto>> listProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) StockStatus stockStatus,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Integer stockMin,
            @RequestParam(required = false) Integer stockMax,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(productService.listProducts(
                buildFilter(query, category, type, stockStatus, status, priceMin, priceMax, stockMin, stockMax, dateFrom, dateTo),
                pageable
        ));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ProductListItemDto> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(productService.getByCode(code));
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductListItemDto> getByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(productService.getByBarcode(barcode));
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<ProductManageDetailDto> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getDetail(id));
    }

    @PostMapping("/detail")
    public ResponseEntity<ProductManageDetailDto> createDetail(@Valid @RequestBody ProductManageSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createDetail(request));
    }

    @PutMapping("/detail/{id}")
    public ResponseEntity<ProductManageDetailDto> updateDetail(
            @PathVariable Long id,
            @Valid @RequestBody ProductManageSaveRequest request
    ) {
        return ResponseEntity.ok(productService.updateDetail(id, request));
    }

    @GetMapping("/{id}/barcodes")
    public ResponseEntity<List<ProductBarcodeDto>> listBarcodes(@PathVariable Long id) {
        return ResponseEntity.ok(productService.listBarcodes(id));
    }

    @PostMapping("/{id}/barcodes")
    public ResponseEntity<ProductBarcodeDto> addBarcode(
            @PathVariable Long id,
            @Valid @RequestBody AddBarcodeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addBarcode(id, request));
    }

    @DeleteMapping("/{id}/barcodes/{barcodeId}")
    public ResponseEntity<Void> deleteBarcode(@PathVariable Long id, @PathVariable Long barcodeId) {
        productService.deleteBarcode(id, barcodeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stock")
    public ResponseEntity<StockSummaryDto> getStockSummary(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getStockSummary(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductStatusUpdateResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(productService.updateStatus(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductListItemDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateLegacyProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return ResponseEntity.ok(productService.updateLegacyProduct(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteById(@PathVariable Long id) {
        productService.softDeleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<ProductDto> createLegacyProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createLegacyProduct(request));
    }

    private ProductSearchFilter buildFilter(
            String query,
            String category,
            ProductType type,
            StockStatus stockStatus,
            ProductStatus status,
            BigDecimal priceMin,
            BigDecimal priceMax,
            Integer stockMin,
            Integer stockMax,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        ProductSearchFilter filter = new ProductSearchFilter();
        filter.setQuery(query);
        filter.setCategory(category);
        filter.setType(type);
        filter.setStockStatus(stockStatus);
        filter.setStatus(status);
        filter.setPriceMin(priceMin);
        filter.setPriceMax(priceMax);
        filter.setStockMin(stockMin);
        filter.setStockMax(stockMax);
        filter.setDateFrom(dateFrom);
        filter.setDateTo(dateTo);
        return filter;
    }
}
