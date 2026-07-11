package com.mgh.backend.product.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    private ProductType type;
    private ProductStatus status;

    @Valid
    private List<AttributeInput> attributes;

    @Valid
    @NotNull
    private List<BarcodeInput> barcodes;

    private Long categoryId;
    private Long manufacturerId;
    private List<Long> supplierIds;

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
        private BigDecimal sellingPrice;
        @NotNull
        private BigDecimal buyingPrice;
        @NotNull
        private Integer stock;

        @JsonProperty("isDefault")
        private boolean isDefault;
    }
}
