package com.mgh.backend.cashier.dto;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ReceiptNavigationWindowResponse {

    private Long currentReceiptId;
    private int currentIndex;
    private boolean hasOlder;
    private boolean hasNewer;
    private List<ReceiptResponseDto> receipts;
}