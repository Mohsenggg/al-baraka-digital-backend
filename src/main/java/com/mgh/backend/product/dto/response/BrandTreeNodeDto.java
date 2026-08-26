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
public class BrandTreeNodeDto {
    private Long id;
    private String code;
    private String name;
    private Long categoryId;
    private int groupCount;
    private int productCount;
    @Builder.Default
    private List<ProductGroupTreeNodeDto> groups = new ArrayList<>();
}
