package com.mgh.backend.product.dto.response;

import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LightweightProductDto {
    private Long id;
    private String barcode;
    private String name;
    private BigDecimal sellingPrice;
    private BigDecimal buyingPrice;
    private Double stock;
    private Long categoryId;
    private Long manufacturerId;
    private Long productGroupId;
    private ProductStatus status;
    private ProductType type;
    private List<ProductDto.RefillOptionDto> refillOptions;

    public LightweightProductDto() {
        this.refillOptions = new ArrayList<>();
    }

    /** Used by JPQL constructor expression in ProductRepository */
    public LightweightProductDto(
            Long id,
            String barcode,
            String name,
            BigDecimal sellingPrice,
            BigDecimal buyingPrice,
            Double stock,
            Long categoryId,
            Long manufacturerId,
            Long productGroupId,
            ProductStatus status,
            ProductType type
    ) {
        this.id = id;
        this.barcode = barcode;
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.buyingPrice = buyingPrice;
        this.stock = stock;
        this.categoryId = categoryId;
        this.manufacturerId = manufacturerId;
        this.productGroupId = productGroupId;
        this.status = status;
        this.type = type;
        this.refillOptions = new ArrayList<>();
    }

    /** Backward-compatible constructor */
    public LightweightProductDto(Long id, String barcode, String name, BigDecimal sellingPrice, BigDecimal buyingPrice, Double stock) {
        this(id, barcode, name, sellingPrice, buyingPrice, stock, null, null, null, null, null);
    }
}
