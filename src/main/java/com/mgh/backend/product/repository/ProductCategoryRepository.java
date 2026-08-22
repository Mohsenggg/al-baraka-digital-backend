package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    Optional<ProductCategory> findByNameIgnoreCase(String name);

    Optional<ProductCategory> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT c.code FROM ProductCategory c WHERE c.code IS NOT NULL")
    Set<String> findAllCodes();

    /** Bulk-load all categories that have a migration code (used by migration service). */
    @Query("SELECT c FROM ProductCategory c WHERE c.code IS NOT NULL")
    List<ProductCategory> findAllWithCode();
}
