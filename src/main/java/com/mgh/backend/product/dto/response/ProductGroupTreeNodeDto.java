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
public class ProductGroupTreeNodeDto {
    private Long id;
    private String code;
    private String name;
    private Long categoryId;
    private Long brandId;
    private int productCount;
    @Builder.Default
    private List<TreeProductItemDto> products = new ArrayList<>();
}
