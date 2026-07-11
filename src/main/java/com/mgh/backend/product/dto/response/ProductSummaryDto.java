package com.mgh.backend.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class ProductSummaryDto {

    private Long defaultBarcodeId;
    private BigDecimal maxSellingPrice;
    private Integer totalStock;
    private Integer barcodeCount;
}
