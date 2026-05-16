package com.mgh.backend.common.lookup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernorateDto {

    private Long id;
    private String nameEn;
    private String nameAr;
}
