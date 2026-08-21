package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.ProductGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface ProductGroupRepository extends JpaRepository<ProductGroup, Long> {

    Optional<ProductGroup> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT pg.code FROM ProductGroup pg")
    Set<String> findAllCodes();
}
