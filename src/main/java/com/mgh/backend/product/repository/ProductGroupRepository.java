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

    /** Bulk-load all product groups with brand and category eagerly fetched (used by migration service and tree service). */
    @Query("SELECT pg FROM ProductGroup pg LEFT JOIN FETCH pg.brand b LEFT JOIN FETCH b.category LEFT JOIN FETCH pg.category ORDER BY pg.name ASC")
    List<ProductGroup> findAllWithBrand();

    @Query("SELECT pg FROM ProductGroup pg LEFT JOIN FETCH pg.brand b LEFT JOIN FETCH b.category LEFT JOIN FETCH pg.category WHERE pg.brand.id = :brandId ORDER BY pg.name ASC")
    List<ProductGroup> findByBrandId(@org.springframework.data.repository.query.Param("brandId") Long brandId);

    @Query("SELECT pg FROM ProductGroup pg LEFT JOIN FETCH pg.category c WHERE pg.category.id = :categoryId AND pg.brand IS NULL ORDER BY pg.name ASC")
    List<ProductGroup> findByCategoryIdAndBrandIsNull(@org.springframework.data.repository.query.Param("categoryId") Long categoryId);

    boolean existsByBrandId(Long brandId);

    boolean existsByCategoryId(Long categoryId);
}
