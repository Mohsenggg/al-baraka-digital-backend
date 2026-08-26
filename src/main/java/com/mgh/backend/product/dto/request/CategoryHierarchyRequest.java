package com.mgh.backend.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryHierarchyRequest {
    @NotBlank(message = "Category name is required")
    private String name;
    private String code;
}
