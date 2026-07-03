package com.mgh.backend.cashier.controller;

import com.mgh.backend.cashier.dto.CreateProductRequest;
import com.mgh.backend.cashier.dto.ProductDto;
import com.mgh.backend.cashier.dto.UpdateProductRequest;
import com.mgh.backend.cashier.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // Existing (modified slightly)
    @GetMapping("/all-products")
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductDto>> searchProducts(
            @RequestParam(defaultValue = "") String query) {
        return ResponseEntity.ok(productService.searchProducts(query));
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductDto> getProductByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(productService.getProductByCode(barcode));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ProductDto> getProductByCode(@PathVariable String code) {
        return ResponseEntity.ok(productService.getProductByCode(code));
    }

    // CREATE
    @PostMapping
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductDto created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // UPDATE
    @PutMapping("/{code}")
    public ResponseEntity<ProductDto> updateProduct(
            @PathVariable String code,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductDto updated = productService.updateProduct(code, request);
        return ResponseEntity.ok(updated);
    }

    // DELETE
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String code) {
        productService.deleteProductByCode(code);
        return ResponseEntity.noContent().build();
    }
}