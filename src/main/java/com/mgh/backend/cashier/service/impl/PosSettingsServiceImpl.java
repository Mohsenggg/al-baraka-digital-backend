package com.mgh.backend.cashier.service.impl;

import com.mgh.backend.cashier.dto.PosSettingItemDto;
import com.mgh.backend.cashier.dto.PosSettingsResponseDto;
import com.mgh.backend.cashier.dto.UpdatePosSettingsBatchRequestDto;
import com.mgh.backend.cashier.entity.PosSetting;
import com.mgh.backend.cashier.exception.BadRequestException;
import com.mgh.backend.cashier.repository.PosSettingsRepository;
import com.mgh.backend.cashier.service.PosSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosSettingsServiceImpl implements PosSettingsService {

    private final PosSettingsRepository posSettingsRepository;

    public static final String KEY_REMAINING_AMOUNT_THRESHOLD = "cashier.payment.remaining_amount_threshold";
    public static final String KEY_ENABLE_REMAINING_AMOUNT_THRESHOLD = "cashier.payment.enable_remaining_amount_threshold";

    private static final Map<String, DefaultSettingDefinition> DEFAULT_DEFINITIONS = Map.of(
            KEY_REMAINING_AMOUNT_THRESHOLD, new DefaultSettingDefinition("50.00", "DECIMAL", "الحد الأدنى لقيمة الفاتورة لإظهار شاشة حساب المتبقي (صرف)"),
            KEY_ENABLE_REMAINING_AMOUNT_THRESHOLD, new DefaultSettingDefinition("true", "BOOLEAN", "تفعيل الفحص الشرطي لشاشة المتبقي بناءً على الحد المالي")
    );

    private record DefaultSettingDefinition(String value, String dataType, String description) {}

    @Override
    @Transactional
    public PosSettingsResponseDto getEffectiveSettings(String scopeType, String scopeId) {
        String effectiveScopeType = normalizeScope(scopeType, "GLOBAL");
        String effectiveScopeId = normalizeScope(scopeId, "DEFAULT");

        seedDefaultSettingsIfMissing();

        List<PosSetting> settingsList = posSettingsRepository.findByScopeTypeAndScopeId(effectiveScopeType, effectiveScopeId);

        // If specific scope requested and empty, fallback to GLOBAL / DEFAULT
        if (settingsList.isEmpty() && (!"GLOBAL".equalsIgnoreCase(effectiveScopeType) || !"DEFAULT".equalsIgnoreCase(effectiveScopeId))) {
            settingsList = posSettingsRepository.findByScopeTypeAndScopeId("GLOBAL", "DEFAULT");
        }

        Map<String, String> flatMap = new LinkedHashMap<>();
        Map<String, PosSettingItemDto> detailMap = new LinkedHashMap<>();

        for (PosSetting s : settingsList) {
            flatMap.put(s.getConfigKey(), s.getConfigValue());
            detailMap.put(s.getConfigKey(), PosSettingItemDto.builder()
                    .configKey(s.getConfigKey())
                    .configValue(s.getConfigValue())
                    .dataType(s.getDataType())
                    .description(s.getDescription())
                    .updatedAt(s.getUpdatedAt())
                    .updatedBy(s.getUpdatedBy())
                    .build());
        }

        return PosSettingsResponseDto.builder()
                .scopeType(effectiveScopeType)
                .scopeId(effectiveScopeId)
                .settings(flatMap)
                .details(detailMap)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String getValue(String configKey, String defaultValue) {
        return posSettingsRepository.findByScopeTypeAndScopeIdAndConfigKey("GLOBAL", "DEFAULT", configKey)
                .map(PosSetting::getConfigValue)
                .orElse(defaultValue);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String configKey, BigDecimal defaultValue) {
        String val = getValue(configKey, null);
        if (val == null) return defaultValue;
        try {
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            log.warn("Invalid decimal setting for key {}: '{}'. Returning default {}", configKey, val, defaultValue);
            return defaultValue;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean getBoolean(String configKey, boolean defaultValue) {
        String val = getValue(configKey, null);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val.trim());
    }

    @Override
    @Transactional
    public PosSettingsResponseDto updateSettingsBatch(UpdatePosSettingsBatchRequestDto request, String username) {
        if (request.getSettings() == null || request.getSettings().isEmpty()) {
            throw new BadRequestException("لا توجد إعدادات محددة للتحديث");
        }

        String scopeType = normalizeScope(request.getScopeType(), "GLOBAL");
        String scopeId = normalizeScope(request.getScopeId(), "DEFAULT");

        for (Map.Entry<String, String> entry : request.getSettings().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            validateSettingValue(key, value);

            Optional<PosSetting> existingOpt = posSettingsRepository.findByScopeTypeAndScopeIdAndConfigKey(scopeType, scopeId, key);
            PosSetting setting;
            if (existingOpt.isPresent()) {
                setting = existingOpt.get();
                setting.setConfigValue(value.trim());
                setting.setUpdatedBy(username);
            } else {
                DefaultSettingDefinition def = DEFAULT_DEFINITIONS.get(key);
                setting = PosSetting.builder()
                        .scopeType(scopeType)
                        .scopeId(scopeId)
                        .configKey(key)
                        .configValue(value.trim())
                        .dataType(def != null ? def.dataType() : "STRING")
                        .description(def != null ? def.description() : key)
                        .updatedBy(username)
                        .build();
            }
            posSettingsRepository.save(setting);
        }

        log.info("POS Settings batch updated by user '{}' for scope {}/{}", username, scopeType, scopeId);
        return getEffectiveSettings(scopeType, scopeId);
    }

    @Override
    @Transactional
    public PosSettingsResponseDto resetKeysToDefaults(List<String> keys, String scopeType, String scopeId, String username) {
        String effectiveScopeType = normalizeScope(scopeType, "GLOBAL");
        String effectiveScopeId = normalizeScope(scopeId, "DEFAULT");

        Collection<String> targetKeys = (keys == null || keys.isEmpty()) ? DEFAULT_DEFINITIONS.keySet() : keys;

        for (String key : targetKeys) {
            DefaultSettingDefinition def = DEFAULT_DEFINITIONS.get(key);
            if (def == null) continue;

            Optional<PosSetting> existing = posSettingsRepository.findByScopeTypeAndScopeIdAndConfigKey(effectiveScopeType, effectiveScopeId, key);
            if (existing.isPresent()) {
                PosSetting s = existing.get();
                s.setConfigValue(def.value());
                s.setUpdatedBy(username);
                posSettingsRepository.save(s);
            } else {
                PosSetting s = PosSetting.builder()
                        .scopeType(effectiveScopeType)
                        .scopeId(effectiveScopeId)
                        .configKey(key)
                        .configValue(def.value())
                        .dataType(def.dataType())
                        .description(def.description())
                        .updatedBy(username)
                        .build();
                posSettingsRepository.save(s);
            }
        }

        log.info("Reset POS Settings keys to default by user '{}' for scope {}/{}", username, effectiveScopeType, effectiveScopeId);
        return getEffectiveSettings(effectiveScopeType, effectiveScopeId);
    }

    @Override
    @Transactional
    public void seedDefaultSettingsIfMissing() {
        for (Map.Entry<String, DefaultSettingDefinition> entry : DEFAULT_DEFINITIONS.entrySet()) {
            String key = entry.getKey();
            DefaultSettingDefinition def = entry.getValue();

            if (!posSettingsRepository.existsByScopeTypeAndScopeIdAndConfigKey("GLOBAL", "DEFAULT", key)) {
                PosSetting s = PosSetting.builder()
                        .scopeType("GLOBAL")
                        .scopeId("DEFAULT")
                        .configKey(key)
                        .configValue(def.value())
                        .dataType(def.dataType())
                        .description(def.description())
                        .updatedBy("SYSTEM_INIT")
                        .build();
                posSettingsRepository.save(s);
                log.info("Seeded default POS setting: {} = {}", key, def.value());
            }
        }
    }

    private void validateSettingValue(String key, String value) {
        if (value == null) {
            throw new BadRequestException("قيمة الإعداد '" + key + "' لا يمكن أن تكون فارغة");
        }

        if (KEY_REMAINING_AMOUNT_THRESHOLD.equals(key)) {
            try {
                BigDecimal dec = new BigDecimal(value.trim());
                if (dec.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BadRequestException("الحد المالي لا يمكن أن يكون قيمة سالبة");
                }
            } catch (NumberFormatException e) {
                throw new BadRequestException("صيغة الحد المالي غير صالحة: '" + value + "'");
            }
        } else if (KEY_ENABLE_REMAINING_AMOUNT_THRESHOLD.equals(key)) {
            String trimmed = value.trim().toLowerCase();
            if (!"true".equals(trimmed) && !"false".equals(trimmed)) {
                throw new BadRequestException("قيمة حالة التفعيل يجب أن تكون true أو false");
            }
        }
    }

    private String normalizeScope(String value, String fallback) {
        return (value != null && !value.trim().isEmpty()) ? value.trim().toUpperCase() : fallback;
    }
}
