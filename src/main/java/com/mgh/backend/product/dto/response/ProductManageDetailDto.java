package com.mgh.backend.product.dto.response;

import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ProductManageDetailDto {

    private Long id;
    private String baseName;
    private String generatedName;
    private ProductType type;
    private ProductStatus status;
    private List<DescAttributeDto> attributes;
    private List<ProductBarcodeFormDto> barcodes;
    private Long categoryId;
    private Long manufacturerId;
    private List<Long> supplierIds;
}
