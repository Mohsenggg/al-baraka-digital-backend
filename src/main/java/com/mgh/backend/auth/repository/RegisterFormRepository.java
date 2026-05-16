package com.mgh.backend.auth.repository;

import com.mgh.backend.auth.domain.entity.RegisterForm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegisterFormRepository extends JpaRepository<RegisterForm, Long> {
}

