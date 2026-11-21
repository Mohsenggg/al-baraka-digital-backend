package com.mgh.backend.cashier.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosSettingItemDto {
    private String configKey;
    private String configValue;
    private String dataType;
    private String description;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
