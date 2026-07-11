package com.mgh.backend.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DescAttributeDto {

    private Long id;
    private String name;
    private String value;
    private Integer ui;
}
