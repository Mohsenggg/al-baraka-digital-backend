package com.mgh.backend.product.dto.response;

import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
public class ProductListItemDto {

    private String id;
    private String name;
    private String code;
    private ProductType type;
    private ProductStatus status;
    private String category;
    private String imageUrl;
    private Integer minStockLevel;
    private Integer maxStockLevel;
    private Instant createdAt;
    private List<DescAttributeDto> descAttributes;
    private List<ProductBarcodeDto> barcodes;
    private ProductSummaryDto summary;
}
