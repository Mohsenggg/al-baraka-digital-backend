package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.ProductGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductGroupRepository extends JpaRepository<ProductGroup, Long> {

    Optional<ProductGroup> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT pg.code FROM ProductGroup pg")
    Set<String> findAllCodes();

    /** Bulk-load all product groups with brand+category eagerly fetched (used by migration service). */
    @Query("SELECT pg FROM ProductGroup pg JOIN FETCH pg.brand b JOIN FETCH b.category")
    List<ProductGroup> findAllWithBrand();
}
