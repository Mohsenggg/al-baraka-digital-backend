package com.mgh.backend.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "product_barcodes",
        indexes = {
                @Index(name = "idx_product_barcodes_barcode", columnList = "barcode"),
                @Index(name = "idx_product_barcodes_product_id", columnList = "product_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 50)
    private String barcode;

    @Column(name = "selling_price", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal sellingPrice;

    @Column(name = "buying_price", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal buyingPrice;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;
}
