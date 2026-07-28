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
    private String name;
    private ProductType type; // Optional/internal
    private ProductStatus status;
    private List<DescAttributeDto> attributes;
    private List<ProductBarcodeFormDto> barcodes;
    private Long categoryId;
    private Long manufacturerId;
    private List<Long> supplierIds;

    private Boolean hasConversion;
    private List<ConversionDto> conversions;

    private Boolean hasMaterials;
    private List<MaterialDto> materials;

    @Getter
    @Setter
    @Builder
    public static class ConversionDto {
        private Long parentProductId;
        private String parentProductName;
        private Integer parentQuantity;
        private Integer childQuantity;
    }

    @Getter
    @Setter
    @Builder
    public static class MaterialDto {
        private Long productId;
        private Double quantity;
    }
}
