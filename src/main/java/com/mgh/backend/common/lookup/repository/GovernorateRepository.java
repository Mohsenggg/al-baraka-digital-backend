package com.mgh.backend.common.lookup.repository;

import com.mgh.backend.common.lookup.entity.Governorate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GovernorateRepository extends JpaRepository<Governorate, Long> {
}
