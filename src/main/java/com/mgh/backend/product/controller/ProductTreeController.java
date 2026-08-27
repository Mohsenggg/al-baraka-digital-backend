package com.mgh.backend.product.controller;

import com.mgh.backend.cashier.dto.PageResponseDto;
import com.mgh.backend.product.dto.response.CategoryChildNodesDto;
import com.mgh.backend.product.dto.response.CategoryTreeNodeDto;
import com.mgh.backend.product.dto.response.ProductGroupTreeNodeDto;
import com.mgh.backend.product.dto.response.ProductTreeResponseDto;
import com.mgh.backend.product.dto.response.ProductTreeStatisticsDto;
import com.mgh.backend.product.dto.response.TreeProductItemDto;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.service.ProductTreeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
