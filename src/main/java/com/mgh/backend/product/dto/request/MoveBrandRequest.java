package com.mgh.backend.product.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveBrandRequest {

    @NotNull(message = "targetCategoryId is required")
    private Long targetCategoryId;
}
