package com.mgh.backend.product.dto.response;

import com.mgh.backend.product.entity.ProductStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class ProductStatusUpdateResponse {

    private Long id;
    private ProductStatus status;
    private Instant updatedAt;
}
