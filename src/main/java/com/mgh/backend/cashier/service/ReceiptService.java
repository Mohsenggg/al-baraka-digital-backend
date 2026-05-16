package com.mgh.backend.cashier.service;

import com.mgh.backend.cashier.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReceiptService {


    ReceiptResponseDto createReceipt(@Valid ReceiptRequestDto request);

    ReceiptResponseDto revokeReceipt(Long id);

    ReceiptResponseDto getReceipt(Long id);

    List<ReceiptResponseDto> getReceipts();

    ReceiptResponseDto updateReceipt(Long id, @Valid ReceiptRequestDto request);
    void deleteReceipt(Long id);
    ReceiptResponseDto draftReceipt(Long id);
    PageResponseDto<ReceiptListItemDto> searchReceipts(ReceiptSearchFilter filter, Pageable pageable);


    ReceiptNavigationWindowResponse getNavigationWindow(Long centerReceiptId, int before, int after);

}