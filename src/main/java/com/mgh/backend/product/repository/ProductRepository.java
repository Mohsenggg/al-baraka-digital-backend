package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.dto.response.LightweightProductDto;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"barcodes"})
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    Optional<Product> findByBarcodeAndDeletedAtIsNull(String barcode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.barcode = :barcode AND p.deletedAt IS NULL")
    Optional<Product> findWithLockByBarcode(@Param("barcode") String barcode);

    @EntityGraph(attributePaths = {"barcodes", "category", "manufacturer"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);

    @Query("SELECT DISTINCT p.barcode FROM Product p WHERE p.deletedAt IS NULL")
    java.util.Set<String> findAllProductBarcodes();

    /** Bulk-load barcode→name pairs for migration existence checks (avoids per-record queries). */
    @Query("SELECT p.barcode, p.name FROM Product p WHERE p.deletedAt IS NULL AND p.barcode IS NOT NULL")
    java.util.List<Object[]> findAllBarcodeAndNamePairs();

    @Query("""
            SELECT p FROM Product p
            WHERE p.id = :id AND p.deletedAt IS NULL
            """)
    Optional<Product> findDetailedById(@Param("id") Long id);

    @Query("""
            SELECT new com.mgh.backend.product.dto.response.LightweightProductDto(
                p.id, 
                pb.barcode, 
                p.name, 
                pb.sellingPrice, 
                pb.buyingPrice,
                COALESCE(SUM(b.stock), 0.0)
            )
            FROM Product p
            LEFT JOIN p.barcodes pb ON pb.isDefault = true AND pb.deletedAt IS NULL
            LEFT JOIN p.barcodes b ON b.deletedAt IS NULL
            WHERE p.deletedAt IS NULL AND p.status = :status
            GROUP BY p.id, p.name, pb.barcode, pb.sellingPrice, pb.buyingPrice
            """)
    List<LightweightProductDto> findAllActiveLightweightProducts(@Param("status") ProductStatus status);

    @Query("""
            SELECT new com.mgh.backend.product.dto.response.RefillOptionProjection(
                c.childProduct.id,
                c.parentProduct.id,
                c.parentProduct.name,
                c.parentQuantity,
                c.childQuantity,
                COALESCE(SUM(b.stock), 0.0),
                c.isDefault
            )
            FROM ProductConversion c
            LEFT JOIN c.parentProduct.barcodes b ON b.deletedAt IS NULL
            WHERE c.childProduct.status = :status AND c.childProduct.deletedAt IS NULL
            GROUP BY c.childProduct.id, c.parentProduct.id, c.parentProduct.name, c.parentQuantity, c.childQuantity, c.isDefault
            """)
    List<com.mgh.backend.product.dto.response.RefillOptionProjection> findActiveRefillOptions(@Param("status") ProductStatus status);
}
