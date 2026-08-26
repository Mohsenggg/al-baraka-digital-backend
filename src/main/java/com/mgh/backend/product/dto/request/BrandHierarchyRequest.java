package com.mgh.backend.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandHierarchyRequest {
    @NotBlank(message = "Brand name is required")
    private String name;
    private String code;
    @NotNull(message = "categoryId is required")
    private Long categoryId;
}
