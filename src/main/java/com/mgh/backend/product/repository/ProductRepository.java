package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.Product;
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

    @EntityGraph(attributePaths = {"barcodes", "attributeValues.attribute", "category", "suppliers"})
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    Optional<Product> findByCodeAndDeletedAtIsNull(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.code = :code AND p.deletedAt IS NULL")
    Optional<Product> findWithLockByCode(@Param("code") String code);

    @EntityGraph(attributePaths = {"barcodes", "attributeValues.attribute", "category"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"barcodes", "attributeValues.attribute", "category"})
    List<Product> findTop50ByDeletedAtIsNullAndNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
            String name,
            String code
    );

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, Long id);

    @EntityGraph(attributePaths = {"barcodes"})
    List<Product> findAllByDeletedAtIsNull();

    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN FETCH p.barcodes b
            LEFT JOIN FETCH p.attributeValues av
            LEFT JOIN FETCH av.attribute
            LEFT JOIN FETCH p.category
            LEFT JOIN FETCH p.suppliers
            LEFT JOIN FETCH p.manufacturer
            WHERE p.id = :id AND p.deletedAt IS NULL
            """)
    Optional<Product> findDetailedById(@Param("id") Long id);
}
