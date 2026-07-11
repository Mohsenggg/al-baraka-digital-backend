package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.Manufacturer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, Long> {

    Optional<Manufacturer> findByNameIgnoreCase(String name);
}
