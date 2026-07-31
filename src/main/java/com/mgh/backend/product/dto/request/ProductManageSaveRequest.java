package com.mgh.backend.product.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductManageSaveRequest {

    @NotBlank
    @Size(max = 255)
    private String baseName;

    @NotBlank
    @Size(max = 500)
    private String name;

    private ProductType type; // Optional/internal
    private ProductStatus status;

    @Valid
    private List<AttributeInput> attributes;

    @Valid
    @NotNull
    private List<BarcodeInput> barcodes;

    private Long categoryId;
    private Long manufacturerId;
    private List<Long> supplierIds;

    private Boolean hasConversion = false;

    @Valid
    private List<ConversionInput> conversions;

    private Boolean hasMaterials = false;

    @Valid
    private List<MaterialInput> materials;

    @Data
    public static class AttributeInput {
        @NotNull
        private Long id;
        private String name;
        @NotBlank
        private String value;
    }

    @Data
    public static class BarcodeInput {
        private Long id;
        @NotBlank
        private String barcode;
        @NotNull
        @PositiveOrZero
        private BigDecimal sellingPrice;
        @NotNull
        @PositiveOrZero
        private BigDecimal buyingPrice;
        @NotNull
        @PositiveOrZero
        private Double stock;

        @JsonProperty("isDefault")
        private boolean isDefault;
    }

    @Data
    public static class ConversionInput {
        @NotNull
        private Long parentProductId;
        @NotNull
        @Min(1)
        private Integer parentQuantity;
        @NotNull
        @Min(1)
        private Integer childQuantity;

        @JsonProperty("isDefault")
        private boolean isDefault;
    }

    @Data
    public static class MaterialInput {
        @NotNull
        private Long productId;
        @NotNull
        @DecimalMin(value = "0.001")
        private Double quantity;
    }
}
