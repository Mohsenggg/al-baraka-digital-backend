package com.mgh.backend.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ReferenceItemDto {

    private Long id;
    private String name;
}
