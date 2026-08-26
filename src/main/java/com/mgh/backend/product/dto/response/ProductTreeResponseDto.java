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
public class ProductTreeResponseDto {
    @Builder.Default
    private List<CategoryTreeNodeDto> tree = new ArrayList<>();
    private ProductTreeStatisticsDto statistics;
}
