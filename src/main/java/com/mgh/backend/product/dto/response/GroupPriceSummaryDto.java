package com.mgh.backend.product.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPriceSummaryDto {

    private Long groupId;
    private String groupName;

    @JsonProperty("isPriceUnified")
    private boolean isPriceUnified;

    private int productCount;

    @Builder.Default
    private List<BigDecimal> distinctSellingPrices = new ArrayList<>();

    private boolean hasPriceDiscrepancy;
}
