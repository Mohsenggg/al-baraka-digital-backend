package com.mgh.backend.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RefillOptionProjection {
    private Long childProductId;
    private Long parentProductId;
    private String parentProductName;
    private Integer parentQuantity;
    private Integer childQuantity;
    private Double parentStock;
    private boolean isDefault;
}

