package com.mgh.backend.product.dto.response;

import com.mgh.backend.product.entity.StockStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class StockSummaryDto {

    private Long productId;
    private Integer totalStock;
    private StockStatus stockStatus;
    private Integer minStockLevel;
    private Integer maxStockLevel;
    private List<StockBarcodeDto> barcodes;

    @Getter
    @Setter
    @Builder
    public static class StockBarcodeDto {
        private Long barcodeId;
        private String barcode;
        private Integer stock;

        @JsonProperty("default")
        private boolean defaultBarcode;
    }
}
