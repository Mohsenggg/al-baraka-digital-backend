package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT b.code FROM Brand b")
    Set<String> findAllCodes();

    /** Bulk-load all brands with their category eagerly fetched (used by migration service and tree service). */
    @Query("SELECT b FROM Brand b JOIN FETCH b.category ORDER BY b.name ASC")
    List<Brand> findAllWithCategory();

    @Query("SELECT b FROM Brand b JOIN FETCH b.category WHERE b.category.id = :categoryId ORDER BY b.name ASC")
    List<Brand> findByCategoryId(@org.springframework.data.repository.query.Param("categoryId") Long categoryId);

    boolean existsByCategoryId(Long categoryId);
}
