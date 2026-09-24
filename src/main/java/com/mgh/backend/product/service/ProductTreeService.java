package com.mgh.backend.product.service;

import com.mgh.backend.cashier.dto.PageResponseDto;
import com.mgh.backend.product.dto.response.BulkMoveProductsResponse;
import com.mgh.backend.product.dto.response.CategoryChildNodesDto;
import com.mgh.backend.product.dto.response.CategoryTreeNodeDto;
import com.mgh.backend.product.dto.response.ProductGroupTreeNodeDto;
import com.mgh.backend.product.dto.response.ProductTreeResponseDto;
import com.mgh.backend.product.dto.response.ProductTreeStatisticsDto;
import com.mgh.backend.product.dto.response.TreeProductItemDto;
import com.mgh.backend.product.entity.ProductStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductTreeService {

    ProductTreeResponseDto getFullTree(
            String query,
            Long categoryId,
            Long brandId,
            String stockStatus,
            ProductStatus status,
            Boolean includeProducts
    );

    ProductTreeStatisticsDto getTreeStatistics();

    List<CategoryTreeNodeDto> getCategorySummaries();

    CategoryChildNodesDto getCategoryChildNodes(Long categoryId);

    List<ProductGroupTreeNodeDto> getBrandGroups(Long brandId);

    PageResponseDto<TreeProductItemDto> getGroupProducts(Long groupId, Pageable pageable);

    // Node Deletion (Empty Only)
    void deleteCategory(Long id);

    void deleteBrand(Long id);

    void deleteProductGroup(Long id);

    // Node Renaming
    void renameCategory(Long id, String newName);

    void renameBrand(Long id, String newName);

    void renameProductGroup(Long id, String newName);

    // Node & Product Moves
    void moveBrand(Long brandId, Long targetCategoryId);

    void moveProductGroup(Long groupId, Long targetBrandId);

    void moveProduct(Long productId, Long targetGroupId);

    BulkMoveProductsResponse bulkMoveProducts(List<Long> productIds, Long targetGroupId);

    // Group Price Unification
    void setProductGroupPriceUnification(Long groupId, boolean isPriceUnified);

    com.mgh.backend.product.dto.response.GroupPriceSummaryDto getGroupPriceSummary(Long groupId);

    void updateGroupSellingPrice(Long groupId, java.math.BigDecimal newSellingPrice);
}
