package com.mgh.backend.cashier.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosSettingsResponseDto {
    private String scopeType;
    private String scopeId;
    private Map<String, String> settings;
    private Map<String, PosSettingItemDto> details;
}
