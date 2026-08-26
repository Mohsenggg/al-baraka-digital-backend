package com.mgh.backend.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTreeNodeDto {
    private Long id;
    private String code;
    private String name;
    private int brandCount;
    private int groupCount;
    private int productCount;
    @Builder.Default
    private List<BrandTreeNodeDto> brands = new ArrayList<>();
    @Builder.Default
    private List<ProductGroupTreeNodeDto> directGroups = new ArrayList<>();
}
