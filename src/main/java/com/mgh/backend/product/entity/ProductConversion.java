package com.mgh.backend.product.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_conversions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductConversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_product_id")
    private Product parentProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_product_id")
    private Product childProduct;

    private Integer parentQuantity;
    private Integer childQuantity;

    @Column(name = "is_default")
    @Builder.Default
    private boolean isDefault = false;
}
