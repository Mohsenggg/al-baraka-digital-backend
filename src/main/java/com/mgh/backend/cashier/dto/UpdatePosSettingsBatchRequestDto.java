package com.mgh.backend.cashier.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePosSettingsBatchRequestDto {

    @Builder.Default
    private String scopeType = "GLOBAL";

    @Builder.Default
    private String scopeId = "DEFAULT";

    @NotEmpty(message = "يجب تحديد إعداد واحد على الأقل للتحديث")
    private Map<String, String> settings;
}
