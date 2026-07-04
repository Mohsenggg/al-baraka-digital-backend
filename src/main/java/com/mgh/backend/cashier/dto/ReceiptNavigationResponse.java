package com.mgh.backend.cashier.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ReceiptNavigationResponse {

    private Long currentReceiptId;
    private Integer currentIndex;
    private Boolean hasPrevious;
    private Boolean hasNext;
    private List<ReceiptResponseDto> receipts;
}
