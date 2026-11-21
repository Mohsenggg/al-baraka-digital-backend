package com.mgh.backend.cashier.repository;

import com.mgh.backend.cashier.entity.PosSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosSettingsRepository extends JpaRepository<PosSetting, Long> {

    Optional<PosSetting> findByScopeTypeAndScopeIdAndConfigKey(String scopeType, String scopeId, String configKey);

    List<PosSetting> findByScopeTypeAndScopeId(String scopeType, String scopeId);

    boolean existsByScopeTypeAndScopeIdAndConfigKey(String scopeType, String scopeId, String configKey);
}
