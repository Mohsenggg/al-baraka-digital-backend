package com.mgh.backend.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LightweightProductDto {
    private Long id;
    private String barcode;
    private String name;
    private BigDecimal price;
    private Long stock;
}
