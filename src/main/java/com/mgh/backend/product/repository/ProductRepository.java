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

    @EntityGraph(attributePaths = {"barcodes"})
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    Optional<Product> findByCodeAndDeletedAtIsNull(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.code = :code AND p.deletedAt IS NULL")
    Optional<Product> findWithLockByCode(@Param("code") String code);

    @EntityGraph(attributePaths = {"barcodes", "category", "manufacturer"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    @Query("""
            SELECT p FROM Product p
            WHERE p.id = :id AND p.deletedAt IS NULL
            """)
    Optional<Product> findDetailedById(@Param("id") Long id);
}
