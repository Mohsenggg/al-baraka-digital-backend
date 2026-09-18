package com.mgh.backend.product.controller;

import com.mgh.backend.cashier.dto.PageResponseDto;
import com.mgh.backend.product.dto.request.BulkMoveProductsRequest;
import com.mgh.backend.product.dto.request.MoveBrandRequest;
import com.mgh.backend.product.dto.request.MoveGroupRequest;
import com.mgh.backend.product.dto.request.MoveProductRequest;
import com.mgh.backend.product.dto.request.RenameNodeRequest;
import com.mgh.backend.product.dto.response.BulkMoveProductsResponse;
import com.mgh.backend.product.dto.response.CategoryChildNodesDto;
import com.mgh.backend.product.dto.response.CategoryTreeNodeDto;
import com.mgh.backend.product.dto.response.ProductGroupTreeNodeDto;
import com.mgh.backend.product.dto.response.ProductTreeResponseDto;
import com.mgh.backend.product.dto.response.ProductTreeStatisticsDto;
import com.mgh.backend.product.dto.response.TreeProductItemDto;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.service.ProductTreeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products/tree")
@RequiredArgsConstructor
public class ProductTreeController {

    private final ProductTreeService productTreeService;

    @GetMapping
    public ResponseEntity<ProductTreeResponseDto> getFullTree(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false, defaultValue = "true") Boolean includeProducts
    ) {
        return ResponseEntity.ok(productTreeService.getFullTree(
                query, categoryId, brandId, stockStatus, status, includeProducts
        ));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ProductTreeStatisticsDto> getTreeStatistics() {
        return ResponseEntity.ok(productTreeService.getTreeStatistics());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryTreeNodeDto>> getCategorySummaries() {
        return ResponseEntity.ok(productTreeService.getCategorySummaries());
    }

    @GetMapping("/categories/{categoryId}/nodes")
    public ResponseEntity<CategoryChildNodesDto> getCategoryChildNodes(@PathVariable Long categoryId) {
        return ResponseEntity.ok(productTreeService.getCategoryChildNodes(categoryId));
    }

    @GetMapping("/brands/{brandId}/groups")
    public ResponseEntity<List<ProductGroupTreeNodeDto>> getBrandGroups(@PathVariable Long brandId) {
        return ResponseEntity.ok(productTreeService.getBrandGroups(brandId));
    }

    @GetMapping("/groups/{groupId}/products")
    public ResponseEntity<PageResponseDto<TreeProductItemDto>> getGroupProducts(
            @PathVariable Long groupId,
            @PageableDefault(size = 100) Pageable pageable
    ) {
        return ResponseEntity.ok(productTreeService.getGroupProducts(groupId, pageable));
    }

    // ==========================================
    // TASK 1: Node Deletion Endpoints (Empty Only)
    // ==========================================

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        productTreeService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/brands/{id}")
    public ResponseEntity<Void> deleteBrand(@PathVariable Long id) {
        productTreeService.deleteBrand(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteProductGroup(@PathVariable Long id) {
        productTreeService.deleteProductGroup(id);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // TASK 2: Node Renaming Endpoints
    // ==========================================

    @PatchMapping("/categories/{id}/rename")
    public ResponseEntity<Map<String, String>> renameCategory(
            @PathVariable Long id,
            @Valid @RequestBody RenameNodeRequest request
    ) {
        productTreeService.renameCategory(id, request.getName());
        return ResponseEntity.ok(Map.of("message", "Category renamed successfully"));
    }

    @PatchMapping("/brands/{id}/rename")
    public ResponseEntity<Map<String, String>> renameBrand(
            @PathVariable Long id,
            @Valid @RequestBody RenameNodeRequest request
    ) {
        productTreeService.renameBrand(id, request.getName());
        return ResponseEntity.ok(Map.of("message", "Brand renamed successfully"));
    }

    @PatchMapping("/groups/{id}/rename")
    public ResponseEntity<Map<String, String>> renameProductGroup(
            @PathVariable Long id,
            @Valid @RequestBody RenameNodeRequest request
    ) {
        productTreeService.renameProductGroup(id, request.getName());
        return ResponseEntity.ok(Map.of("message", "Product group renamed successfully"));
    }

    // ==========================================
    // TASK 3: Node & Product Move Endpoints
    // ==========================================

    @PostMapping("/brands/{id}/move")
    public ResponseEntity<Map<String, String>> moveBrand(
            @PathVariable Long id,
            @Valid @RequestBody MoveBrandRequest request
    ) {
        productTreeService.moveBrand(id, request.getTargetCategoryId());
        return ResponseEntity.ok(Map.of("message", "Brand moved successfully"));
    }

    @PostMapping("/groups/{id}/move")
    public ResponseEntity<Map<String, String>> moveProductGroup(
            @PathVariable Long id,
            @Valid @RequestBody MoveGroupRequest request
    ) {
        productTreeService.moveProductGroup(id, request.getTargetBrandId());
        return ResponseEntity.ok(Map.of("message", "Product group moved successfully"));
    }

    @PostMapping("/products/{id}/move")
    public ResponseEntity<Map<String, String>> moveProduct(
            @PathVariable Long id,
            @Valid @RequestBody MoveProductRequest request
    ) {
        productTreeService.moveProduct(id, request.getTargetGroupId());
        return ResponseEntity.ok(Map.of("message", "Product moved successfully"));
    }

    @PostMapping("/products/bulk-move")
    public ResponseEntity<BulkMoveProductsResponse> bulkMoveProducts(
            @Valid @RequestBody BulkMoveProductsRequest request
    ) {
        BulkMoveProductsResponse response = productTreeService.bulkMoveProducts(
                request.getProductIds(), request.getTargetGroupId()
        );
        return ResponseEntity.ok(response);
    }
}
