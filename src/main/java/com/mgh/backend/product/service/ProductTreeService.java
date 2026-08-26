package com.mgh.backend.product.service;

import com.mgh.backend.cashier.dto.PageResponseDto;
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
}
