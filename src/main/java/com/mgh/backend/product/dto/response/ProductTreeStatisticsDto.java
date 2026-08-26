package com.mgh.backend.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductTreeStatisticsDto {
    private long totalCategories;
    private long totalBrands;
    private long totalGroups;
    private long totalProducts;
    private double totalStockUnits;
    private long activeProductsCount;
    private long lowStockProductsCount;
    private long outOfStockProductsCount;
}
