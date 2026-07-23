package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

//    Optional<ProductCategory> findBySlugIgnoreCase(String slug);

    Optional<ProductCategory> findByNameIgnoreCase(String name);
}
