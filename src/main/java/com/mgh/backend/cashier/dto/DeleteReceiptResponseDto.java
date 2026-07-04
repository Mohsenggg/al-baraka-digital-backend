package com.mgh.backend.cashier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class DeleteReceiptResponseDto {
    private Long id;
    private boolean isDeleted;
}
