package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT b.code FROM Brand b")
    Set<String> findAllCodes();
}
