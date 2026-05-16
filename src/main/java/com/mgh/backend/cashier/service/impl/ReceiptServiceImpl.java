package com.mgh.backend.cashier.service.impl;


import com.mgh.backend.cashier.dto.*;
import com.mgh.backend.cashier.entity.*;
import com.mgh.backend.cashier.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReceiptServiceImpl {


    private final ReceiptRepository receiptRepository;
    private final CashierRepository cashierRepository;
    private final ProductRepository productRepository;

    // ──────────────────────────────────────────────
    //  Create (client handles pricing, we deduct stock)
    // ──────────────────────────────────────────────
    public ReceiptResponseDto createReceipt(ReceiptRequestDto request) {
        Cashier cashier = cashierRepository.findById(request.getCashierId()).orElseThrow(() -> new EntityNotFoundException("Cashier not found with id: " + request.getCashierId()));


        Receipt receipt = Receipt.builder()
                .receiptNumber(generateReceiptNumber())
                .receiptDate(LocalDateTime.now())
                .paymentMethod(parsePaymentMethod(request.getPaymentMethod()))
                .receiptType(parseReceiptType(request.getReceiptType()))
                .customerName(request.getCustomerName())
                .status(ReceiptStatus.SAVED)
                .cashier(cashier)
                .isDeleted(false)
                .build();

        // Build items using client data AND deduct stock
        List<ReceiptItem> items = request.getItems().stream()
                .map(itemReq -> buildItemAndDeductStock(itemReq, receipt))
                .collect(Collectors.toList());
        receipt.setItems(items);

        recalculateTotals(receipt);
        Receipt saved = receiptRepository.save(receipt);
        return mapToResponseDto(saved);
    }

    // ──────────────────────────────────────────────
    //  Update (replace all items, adjust stock)
    // ──────────────────────────────────────────────
    public ReceiptResponseDto updateReceipt(Long id, ReceiptRequestDto request) {
        Receipt receipt = receiptRepository.findActiveById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found with id: " + id));
        if (receipt.getStatus() == ReceiptStatus.DELETED) {
            throw new IllegalStateException ("Cannot update a deleted receipt");
        }

        // First, restore stock for all the OLD items
        for (ReceiptItem oldItem : receipt.getItems()) {
            Product product = productRepository.findByCode(oldItem.getProductCode())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found for code: " + oldItem.getProductCode()));
            product.setStock(product.getStock() + oldItem.getQuantity());
            productRepository.save(product);
        }

        // Update receipt metadata
        if (request.getPaymentMethod() != null) {
            receipt.setPaymentMethod(parsePaymentMethod(request.getPaymentMethod()));
        }
        if (request.getReceiptType() != null) {
            receipt.setReceiptType(parseReceiptType(request.getReceiptType()));
        }
        if (request.getCustomerName() != null) {
            receipt.setCustomerName(request.getCustomerName());
        }

        // Clear old items and add new ones with fresh stock deduction
        receipt.getItems().clear();
        List<ReceiptItem> newItems = request.getItems().stream()
                .map(itemReq -> buildItemAndDeductStock(itemReq, receipt))
                .collect(Collectors.toList());
        receipt.getItems().addAll(newItems);

        recalculateTotals(receipt);
        return mapToResponseDto(receipt);
    }

    // ──────────────────────────────────────────────
    //  Delete (soft delete, restore stock)
    // ──────────────────────────────────────────────
    public void deleteReceipt(Long id) {
        Receipt receipt = receiptRepository.findActiveById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found with id: " + id));

        // Restore stock before deletion
        for (ReceiptItem item : receipt.getItems()) {
            Product product = productRepository.findByCode(item.getProductCode())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found for code: " + item.getProductCode()));
            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);
        }

        receipt.setDeleted(true);
        receipt.setStatus(ReceiptStatus.DELETED);
        receiptRepository.save(receipt);
    }

    // ──────────────────────────────────────────────
    //  Revoke (just change status, no stock change)
    // ──────────────────────────────────────────────
    public ReceiptResponseDto revokeReceipt(Long id) {
        Receipt receipt = receiptRepository.findActiveById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found with id: " + id));
        if (receipt.getStatus() == ReceiptStatus.DELETED) {
            throw new IllegalStateException("Receipt is already revoked");
        }
        receipt.setStatus(ReceiptStatus.DELETED);
        receiptRepository.save(receipt);
        return mapToResponseDto(receipt);
    }

    // ──────────────────────────────────────────────
    //  Draft (move from SAVED back to DRAFT)
    // ──────────────────────────────────────────────
    public ReceiptResponseDto draftReceipt(Long id) {
        Receipt receipt = receiptRepository.findActiveById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found with id: " + id));
        if (receipt.getStatus() != ReceiptStatus.SAVED) {
            throw new IllegalStateException ("Only saved receipts can be moved to draft");
        }
        receipt.setStatus(ReceiptStatus.DRAFT);
        receiptRepository.save(receipt);
        return mapToResponseDto(receipt);
    }

    // ──────────────────────────────────────────────
    //  Read operations
    // ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ReceiptResponseDto getReceipt(Long id) {
        Receipt receipt = receiptRepository.findActiveById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found with id: " + id));
        return mapToResponseDto(receipt);
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponseDto> getReceipts() {
        return receiptRepository.findAllActive()
                .stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }



    @Transactional(readOnly = true)
    public PageResponseDto<ReceiptListItemDto> searchReceipts(ReceiptSearchFilter filter, Pageable pageable) {
        Page<Receipt> receiptPage = receiptRepository.findAll(
                ReceiptSpecification.withFilters(filter),
                pageable
        );

        return PageResponseDto.<ReceiptListItemDto>builder()
                .content(
                        receiptPage.getContent()
                                .stream()
                                .map(this::mapToListItemDto)
                                .toList()
                )
                .page(receiptPage.getNumber())
                .size(receiptPage.getSize())
                .totalElements(receiptPage.getTotalElements())
                .totalPages(receiptPage.getTotalPages())
                .hasNext(receiptPage.hasNext())
                .hasPrevious(receiptPage.hasPrevious())
                .build();
    }

    private ReceiptListItemDto mapToListItemDto(Receipt receipt) {
        return ReceiptListItemDto.builder()
                .id(receipt.getId())
                .receiptCode(receipt.getReceiptNumber())
                .receiptDate(receipt.getReceiptDate())
                .totalAmount(receipt.getTotalAmount())
                .totalItems(receipt.getTotalItems())
                .customerName(receipt.getCustomerName())
                .status(receipt.getStatus())
                .paymentMethod(receipt.getPaymentMethod())
                .cashierName(
                        receipt.getCashier() != null
                                ? receipt.getCashier().getFullName()
                                : null
                )
                .build();
    }





    @Transactional(readOnly = true)
    public ReceiptNavigationWindowResponse getNavigationWindow(Long centerReceiptId, int before, int after) {
        int safeBefore = Math.max(before, 0);
        int safeAfter = Math.max(after, 0);

        if (centerReceiptId == null) {
            return getLatestNavigationWindow(safeAfter);
        }

        Receipt centerReceipt = receiptRepository.findActiveWithItemsById(centerReceiptId)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found with id: " + centerReceiptId));

        List<Receipt> newerReceipts = receiptRepository.findNewerThan(
                centerReceiptId,
                PageRequest.of(0, safeBefore)
        );

        Collections.reverse(newerReceipts);

        List<Receipt> olderReceipts = receiptRepository.findOlderThan(
                centerReceiptId,
                PageRequest.of(0, safeAfter)
        );

        List<Receipt> receipts = new ArrayList<>();
        receipts.addAll(newerReceipts);
        receipts.add(centerReceipt);
        receipts.addAll(olderReceipts);

        return ReceiptNavigationWindowResponse.builder()
                .currentReceiptId(centerReceipt.getId())
                .currentIndex(newerReceipts.size())
                .hasOlder(receiptRepository.existsByIsDeletedFalseAndIdLessThan(centerReceipt.getId()))
                .hasNewer(receiptRepository.existsByIsDeletedFalseAndIdGreaterThan(centerReceipt.getId()))
                .receipts(
                        receipts.stream()
                                .map(this::mapToResponseDto)
                                .toList()
                )
                .build();
    }

    private ReceiptNavigationWindowResponse getLatestNavigationWindow(int after) {
        List<Receipt> receipts = receiptRepository.findLatestNavigationWindow(
                PageRequest.of(0, after + 1)
        );

        if (receipts.isEmpty()) {
            return ReceiptNavigationWindowResponse.builder()
                    .currentReceiptId(null)
                    .currentIndex(0)
                    .hasOlder(false)
                    .hasNewer(false)
                    .receipts(List.of())
                    .build();
        }

        Receipt latestReceipt = receipts.getFirst();

        return ReceiptNavigationWindowResponse.builder()
                .currentReceiptId(latestReceipt.getId())
                .currentIndex(0)
                .hasOlder(receiptRepository.existsByIsDeletedFalseAndIdLessThan(latestReceipt.getId()))
                .hasNewer(false)
                .receipts(
                        receipts.stream()
                                .map(this::mapToResponseDto)
                                .toList()
                )
                .build();
    }

    // ──────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────

    /**
     * Build a ReceiptItem from the client DTO, fetch the product
     * (for the FK relationship), and deduct stock immediately.
     */
    private ReceiptItem buildItemAndDeductStock(ReceiptItemRequest itemReq, Receipt receipt) {
        Product product = productRepository.findByCode(itemReq.getProductCode())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + itemReq.getProductCode()));

        // Deduct stock
        if (product.getStock() < itemReq.getQuantity()) {
            throw new IllegalStateException(
                    "Insufficient stock for product " + product.getCode() +
                            ": available " + product.getStock() + ", requested " + itemReq.getQuantity());
        }
        product.setStock(product.getStock() - itemReq.getQuantity());
        productRepository.save(product);

        return ReceiptItem.builder()
                .receipt(receipt)
                .productCode(itemReq.getProductCode())
                .productName(product.getName())
                .quantity(itemReq.getQuantity())
                .price(itemReq.getPrice())    // client's unit price
                .total(itemReq.getTotal())
                .remainingStock(product.getStock())// client's line total
                .build();
    }

    private void recalculateTotals(Receipt receipt) {
        int totalQty = receipt.getItems().stream()
                .mapToInt(ReceiptItem::getQuantity).sum();
        BigDecimal totalAmt = receipt.getItems().stream()
                .map(ReceiptItem::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        receipt.setTotalQuantity(totalQty);
        receipt.setTotalItems(receipt.getItems().size());
        receipt.setTotalAmount(totalAmt);
    }

    private String generateReceiptNumber() {
        String datePart = LocalDateTime.now().toLocalDate().toString().replace("-", "");
        String uuid = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "INV-" + datePart + "-" + uuid;
    }

    private PaymentMethod parsePaymentMethod(String method) {
        if (method == null) return PaymentMethod.CASH;
        try {
            return PaymentMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid payment method: " + method);
        }
    }

    private ReceiptType parseReceiptType(String type) {
        if (type == null) return ReceiptType.SELL;
        try {
            return ReceiptType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid receipt type: " + type);
        }
    }

    // ──────────────────────────────────────────────
    //  DTO mapping
    // ──────────────────────────────────────────────
    private ReceiptResponseDto mapToResponseDto(Receipt receipt) {
        List<ReceiptItemResponse> itemDtos = receipt.getItems().stream()
                .map(this::mapItemToDto)
                .collect(Collectors.toList());

        return ReceiptResponseDto.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .receiptDate(receipt.getReceiptDate())
                .paymentMethod(receipt.getPaymentMethod())
                .totalAmount(receipt.getTotalAmount())
                .totalQuantity(receipt.getTotalQuantity())
                .totalItems(receipt.getTotalItems())
                .receiptType(receipt.getReceiptType())
                .status(receipt.getStatus())
                .customerName(receipt.getCustomerName())
                .cashierId(receipt.getCashier().getId())
                .cashierName(receipt.getCashier().getFullName() != null ? receipt.getCashier().getFullName() : "Unknown")
                .items(itemDtos)
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }

    private ReceiptItemResponse mapItemToDto(ReceiptItem item) {
        return ReceiptItemResponse.builder()
                .productCode(item.getProductCode())
                .productName(item.getProductName())
                .unitPrice(item.getPrice())            // client's price, saved as unit_price
                .quantity(item.getQuantity())
                .totalPrice(item.getTotal())           // client's total
                .remainingStock(item.getRemainingStock()) // stock AFTER deduction
                .build();
    }
}