package com.mgh.backend.cashier.repository;

import com.mgh.backend.cashier.entity.Cashier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface CashierRepository extends JpaRepository<Cashier, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<Cashier> findByUsername(String username);

    Optional<Cashier> findByEmail(String email);
}
