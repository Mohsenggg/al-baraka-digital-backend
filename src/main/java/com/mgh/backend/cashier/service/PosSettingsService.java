package com.mgh.backend.cashier.service;

import com.mgh.backend.cashier.dto.PosSettingsResponseDto;
import com.mgh.backend.cashier.dto.UpdatePosSettingsBatchRequestDto;

import java.math.BigDecimal;
import java.util.List;

public interface PosSettingsService {

    PosSettingsResponseDto getEffectiveSettings(String scopeType, String scopeId);

    String getValue(String configKey, String defaultValue);

    BigDecimal getDecimal(String configKey, BigDecimal defaultValue);

    boolean getBoolean(String configKey, boolean defaultValue);

    PosSettingsResponseDto updateSettingsBatch(UpdatePosSettingsBatchRequestDto request, String username);

    PosSettingsResponseDto resetKeysToDefaults(List<String> keys, String scopeType, String scopeId, String username);

    void seedDefaultSettingsIfMissing();
}
