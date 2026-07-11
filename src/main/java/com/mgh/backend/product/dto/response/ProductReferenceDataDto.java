package com.mgh.backend.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ProductReferenceDataDto {

    private List<ReferenceItemDto> attributes;
    private List<ReferenceItemDto> categories;
    private List<ReferenceItemDto> manufacturers;
    private List<ReferenceItemDto> suppliers;
}
