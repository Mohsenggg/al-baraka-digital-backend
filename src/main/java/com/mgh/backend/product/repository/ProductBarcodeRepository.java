package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.ProductBarcode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM ProductBarcode b
            JOIN FETCH b.product p
            WHERE b.barcode = :barcode AND b.deletedAt IS NULL AND p.deletedAt IS NULL
            """)
    Optional<ProductBarcode> findWithLockByBarcode(@Param("barcode") String barcode);

    @Query("""
            SELECT b FROM ProductBarcode b
            JOIN FETCH b.product p
            LEFT JOIN FETCH p.barcodes
            LEFT JOIN FETCH p.category
            WHERE b.barcode = :barcode AND b.deletedAt IS NULL AND p.deletedAt IS NULL
            """)
    Optional<ProductBarcode> findActiveByBarcode(@Param("barcode") String barcode);

    Optional<ProductBarcode> findByIdAndProductIdAndDeletedAtIsNull(Long id, Long productId);

    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);
}
