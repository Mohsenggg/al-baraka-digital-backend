package com.mgh.backend.product.controller;

import com.mgh.backend.cashier.dto.PageResponseDto;
import com.mgh.backend.product.dto.ProductSearchFilter;
import com.mgh.backend.product.dto.request.ProductManageSaveRequest;
import com.mgh.backend.product.dto.response.LightweightProductDto;
import com.mgh.backend.product.dto.response.ProductIdResponse;
import com.mgh.backend.product.dto.response.ProductListItemDto;
import com.mgh.backend.product.dto.response.ProductManageDetailDto;
import com.mgh.backend.product.dto.response.ProductDto;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/all-products")
    public ResponseEntity<List<LightweightProductDto>> getAllActiveProducts() {
        return ResponseEntity.ok(productService.getAllActiveProducts());
    }

    @GetMapping
    public ResponseEntity<PageResponseDto<ProductListItemDto>> listProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long manufacturerId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(page = 0, size = 100, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        ProductSearchFilter filter = new ProductSearchFilter();
        filter.setQuery(query);
        filter.setCategoryId(categoryId);
        filter.setManufacturerId(manufacturerId);
        filter.setSupplierId(supplierId);
        filter.setStatus(status);

        return ResponseEntity.ok(productService.listProducts(filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductManageDetailDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductDto> getByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(productService.getByBarcode(barcode));
    }

    @PostMapping
    public ResponseEntity<ProductIdResponse> createProduct(@Valid @RequestBody ProductManageSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductIdResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductManageSaveRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
