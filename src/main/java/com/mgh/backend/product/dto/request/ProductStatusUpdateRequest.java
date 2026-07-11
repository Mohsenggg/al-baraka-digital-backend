package com.mgh.backend.product.dto.request;

import com.mgh.backend.product.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductStatusUpdateRequest {

    @NotNull
    private ProductStatus status;
}
