package com.mgh.backend.product.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkMoveProductsRequest {

    @NotEmpty(message = "productIds list cannot be empty")
    private List<Long> productIds;

    @NotNull(message = "targetGroupId is required")
    private Long targetGroupId;
}
