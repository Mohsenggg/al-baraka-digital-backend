package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    Optional<ProductAttribute> findByNameIgnoreCase(String name);
}
