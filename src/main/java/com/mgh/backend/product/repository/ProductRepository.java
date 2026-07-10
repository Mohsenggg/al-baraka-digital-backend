package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findWithLockByCode(String code);

    List<Product> findTop20ByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            String code,
            String name
    );

    boolean existsByCode(String code);
}
