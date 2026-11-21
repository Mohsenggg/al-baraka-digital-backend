package com.mgh.backend.cashier.service.impl;

import com.mgh.backend.cashier.dto.*;
import com.mgh.backend.cashier.entity.*;
import com.mgh.backend.cashier.exception.ResourceNotFoundException;
import com.mgh.backend.cashier.exception.BusinessException;
import com.mgh.backend.cashier.port.CashierProductPort;
import com.mgh.backend.cashier.port.CashierSaleProduct;
import com.mgh.backend.cashier.repository.*;
import com.mgh.backend.cashier.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReceiptServiceImpl implements ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptServiceImpl.class);

    private final ReceiptRepository receiptRepository;
    private final CashierRepository cashierRepository;
    private final CashierProductPort cashierProductPort;

    @Override
    public ReceiptResponseDto createReceipt(ReceiptRequestDto request) {
        Cashier cashier = cashierRepository.findById(request.getCashierId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Cashier not found with id: " + request.getCashierId()));

        Receipt receipt = Receipt.builder()
                .receiptNumber(generateUniqueReceiptNumber())
                .receiptDate(LocalDateTime.now())
                .paymentMethod(parsePaymentMethod(request.getPaymentMethod()))
                .receiptType(parseReceiptType(request.getReceiptType()))
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .tax(request.getTax())
                .discount(request.getDiscount())
                .status(ReceiptStatus.SAVED)
                .cashier(cashier)
                .isDeleted(false)
                .build();

        List<ReceiptItem> items = request.getItems().stream()
                .map(itemReq -> buildItemAndDeductStock(itemReq, receipt))
                .collect(Collectors.toList());
        receipt.setItems(items);

        recalculateTotals(receipt);
        Receipt saved = receiptRepository.save(receipt);

        log.info("Receipt created: {} by cashier: {}", saved.getReceiptNumber(), cashier.getFullName());
        return mapToResponseDto(saved);
    }

    @Override
    public ReceiptResponseDto updateReceipt(Long id, ReceiptRequestDto request) {
        Receipt receipt = receiptRepository.findActiveWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + id));

        if (receipt.getStatus() == ReceiptStatus.DELETED) {
            throw new BusinessException("Cannot update a deleted receipt");
        }

        restoreStockForItems(receipt.getItems());

        if (request.getPaymentMethod() != null) {
            receipt.setPaymentMethod(parsePaymentMethod(request.getPaymentMethod()));
        }
        if (request.getReceiptType() != null) {
            receipt.setReceiptType(parseReceiptType(request.getReceiptType()));
        }
        if (request.getCustomerName() != null) {
            receipt.setCustomerName(request.getCustomerName());
        }
        if (request.getCustomerPhone() != null) {
            receipt.setCustomerPhone(request.getCustomerPhone());
        }
        receipt.setTax(request.getTax());
        receipt.setDiscount(request.getDiscount());

        Map<String, ReceiptItem> existingItemsByBarcode = receipt.getItems().stream()
                .collect(Collectors.toMap(ReceiptItem::getProductCode, item -> item, (e, r) -> e));

        Set<String> requestedBarcodes = request.getItems().stream()
                .map(ReceiptItemRequest::getProductCode)
                .collect(Collectors.toSet());

        receipt.getItems().removeIf(item -> !requestedBarcodes.contains(item.getProductCode()));

        for (ReceiptItemRequest itemReq : request.getItems()) {
            ReceiptItem existingItem = existingItemsByBarcode.get(itemReq.getProductCode());
            if (existingItem != null) {
                updateItemAndDeductStock(existingItem, itemReq);
            } else {
                ReceiptItem newItem = buildItemAndDeductStock(itemReq, receipt);
                receipt.addItem(newItem);
            }
        }

        recalculateTotals(receipt);
        Receipt saved = receiptRepository.save(receipt);

        log.info("Receipt updated: {}", saved.getReceiptNumber());
        return mapToResponseDto(saved);
    }

    @Override
    public DeleteReceiptResponseDto deleteReceipt(Long id) {
        Receipt receipt = receiptRepository.findActiveWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + id));

        if (receipt.isDeleted() || receipt.getStatus() == ReceiptStatus.DELETED) {
            throw new BusinessException("Receipt is already deleted");
        }

        if (receipt.getStatus() == ReceiptStatus.DRAFT) {
            return deleteTemporaryReceipt(id);
        }

        List<ReceiptItem> items = receipt.getItems();
        if (items == null || items.isEmpty()) {
            throw new BusinessException("Cannot delete a receipt with no items");
        }

        restoreStockForItems(items);

        receipt.setDeleted(true);
        receipt.setStatus(ReceiptStatus.DELETED);
        receiptRepository.save(receipt);

        log.info("Receipt soft-deleted: {} (restored stock for {} item(s))",
                receipt.getReceiptNumber(), items.size());
        return DeleteReceiptResponseDto.builder()
                .id(id)
                .isDeleted(true)
                .build();
    }

    @Override
    public ReceiptResponseDto revokeReceipt(Long id) {
        Receipt receipt = receiptRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + id));

        if (receipt.getStatus() == ReceiptStatus.DELETED) {
            throw new BusinessException("Receipt is already revoked");
        }

        receipt.setStatus(ReceiptStatus.DELETED);
        receiptRepository.save(receipt);

        log.info("Receipt revoked: {}", receipt.getReceiptNumber());
        return mapToResponseDto(receipt);
    }

    @Override
    public ReceiptResponseDto draftReceipt(Long id) {
        Receipt receipt = receiptRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + id));

        if (receipt.getStatus() != ReceiptStatus.SAVED) {
            throw new BusinessException("Only saved receipts can be moved to draft");
        }

        receipt.setStatus(ReceiptStatus.DRAFT);
        receiptRepository.save(receipt);

        log.info("Receipt moved to draft: {}", receipt.getReceiptNumber());
        return mapToResponseDto(receipt);
    }

    @Override
    public ReceiptResponseDto createTemporaryReceipt(ReceiptRequestDto request) {
        Cashier cashier = cashierRepository.findById(request.getCashierId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Cashier not found with id: " + request.getCashierId()));

        Receipt receipt = Receipt.builder()
                .receiptNumber(generateUniqueReceiptNumber())
                .receiptDate(LocalDateTime.now())
                .paymentMethod(parsePaymentMethod(request.getPaymentMethod()))
                .receiptType(parseReceiptType(request.getReceiptType()))
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .tax(request.getTax())
                .discount(request.getDiscount())
                .status(ReceiptStatus.DRAFT)
                .cashier(cashier)
                .isDeleted(false)
                .build();

        List<ReceiptItem> items = request.getItems().stream()
                .map(itemReq -> buildItemWithoutStockDeduction(itemReq, receipt))
                .collect(Collectors.toList());
        receipt.setItems(items);

        recalculateTotals(receipt);
        Receipt saved = receiptRepository.save(receipt);

        log.info("Temporary receipt created: {} by cashier: {}", saved.getReceiptNumber(), cashier.getFullName());
        return mapToResponseDto(saved);
    }

    @Override
    public ReceiptResponseDto updateTemporaryReceipt(Long id, ReceiptRequestDto request) {
        Receipt receipt = receiptRepository.findActiveWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + id));

        if (receipt.isDeleted() || receipt.getStatus() == ReceiptStatus.DELETED) {
            throw new BusinessException("Cannot update a deleted receipt");
        }
        if (receipt.getStatus() != ReceiptStatus.DRAFT) {
            throw new BusinessException("Receipt is not a temporary receipt");
        }

        if (request.getPaymentMethod() != null) {
            receipt.setPaymentMethod(parsePaymentMethod(request.getPaymentMethod()));
        }
        if (request.getReceiptType() != null) {
            receipt.setReceiptType(parseReceiptType(request.getReceiptType()));
        }
        if (request.getCustomerName() != null) {
            receipt.setCustomerName(request.getCustomerName());
        }
        if (request.getCustomerPhone() != null) {
            receipt.setCustomerPhone(request.getCustomerPhone());
        }
        receipt.setTax(request.getTax());
        receipt.setDiscount(request.getDiscount());

        Map<String, ReceiptItem> existingItemsByBarcode = receipt.getItems().stream()
                .collect(Collectors.toMap(ReceiptItem::getProductCode, item -> item, (e, r) -> e));

        Set<String> requestedBarcodes = request.getItems().stream()
                .map(ReceiptItemRequest::getProductCode)
                .collect(Collectors.toSet());

        // 1. Remove items no longer in request (orphanRemoval deletes them from DB)
        receipt.getItems().removeIf(item -> !requestedBarcodes.contains(item.getProductCode()));

        // 2. Update existing items in-place or add newly introduced items
        for (ReceiptItemRequest itemReq : request.getItems()) {
            ReceiptItem existingItem = existingItemsByBarcode.get(itemReq.getProductCode());
            if (existingItem != null) {
                updateItemWithoutStockDeduction(existingItem, itemReq);
            } else {
                ReceiptItem newItem = buildItemWithoutStockDeduction(itemReq, receipt);
                receipt.addItem(newItem);
            }
        }

        recalculateTotals(receipt);
        Receipt saved = receiptRepository.save(receipt);

        log.info("Temporary receipt updated: {}", saved.getReceiptNumber());
        return mapToResponseDto(saved);
    }

    @Override
    public ReceiptResponseDto completeTemporaryReceipt(Long id, ReceiptRequestDto request) {
        Receipt receipt = receiptRepository.findActiveWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + id));

        if (receipt.isDeleted() || receipt.getStatus() == ReceiptStatus.DELETED) {
            throw new BusinessException("Cannot complete a deleted receipt");
        }
        if (receipt.getStatus() != ReceiptStatus.DRAFT) {
            throw new BusinessException("Only temporary receipts can be completed via this operation");
        }

        if (request.getPaymentMethod() != null) {
            receipt.setPaymentMethod(parsePaymentMethod(request.getPaymentMethod()));
        }
        if (request.getReceiptType() != null) {
            receipt.setReceiptType(parseReceiptType(request.getReceiptType()));
        }
        if (request.getCustomerName() != null) {
            receipt.setCustomerName(request.getCustomerName());
        }
        if (request.getCustomerPhone() != null) {
            receipt.setCustomerPhone(request.getCustomerPhone());
        }
        receipt.setTax(request.getTax());
        receipt.setDiscount(request.getDiscount());
        receipt.setReceiptDate(LocalDateTime.now());
        receipt.setStatus(ReceiptStatus.SAVED);

        Map<String, ReceiptItem> existingItemsByBarcode = receipt.getItems().stream()
                .collect(Collectors.toMap(ReceiptItem::getProductCode, item -> item, (e, r) -> e));

        Set<String> requestedBarcodes = request.getItems().stream()
                .map(ReceiptItemRequest::getProductCode)
                .collect(Collectors.toSet());

        // 1. Remove items no longer in request (no stock was ever deducted for temp
        // receipts)
        receipt.getItems().removeIf(item -> !requestedBarcodes.contains(item.getProductCode()));

        // 2. Update existing items (deducting stock) or add newly introduced items
        // (deducting stock)
        for (ReceiptItemRequest itemReq : request.getItems()) {
            ReceiptItem existingItem = existingItemsByBarcode.get(itemReq.getProductCode());
            if (existingItem != null) {
                updateItemAndDeductStock(existingItem, itemReq);
            } else {
                ReceiptItem newItem = buildItemAndDeductStock(itemReq, receipt);
                receipt.addItem(newItem);
            }
        }

        recalculateTotals(receipt);
        Receipt saved = receiptRepository.save(receipt);

        log.info("Temporary receipt completed to permanent sale: {}", saved.getReceiptNumber());
        return mapToResponseDto(saved);
    }

    @Override
    public DeleteReceiptResponseDto deleteTemporaryReceipt(Long id) {
        Receipt receipt = receiptRepository.findActiveWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + id));

        if (receipt.isDeleted() || receipt.getStatus() == ReceiptStatus.DELETED) {
            throw new BusinessException("Receipt is already deleted");
        }
        if (receipt.getStatus() != ReceiptStatus.DRAFT) {
            throw new BusinessException("Receipt is not a temporary receipt. Use standard delete for saved receipts.");
        }

        // Deliberately do not restore stock because temporary receipts never deducted
        // stock!
        receipt.setDeleted(true);
        receipt.setStatus(ReceiptStatus.DELETED);
        receiptRepository.save(receipt);

        log.info("Temporary receipt deleted: {}", receipt.getReceiptNumber());
        return DeleteReceiptResponseDto.builder()
                .id(id)
                .isDeleted(true)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReceiptListItemDto> getTemporaryReceipts() {
        return receiptRepository.findActiveDrafts()
                .stream()
                .map(this::mapToListItemDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getTemporaryReceiptsCount() {
        return receiptRepository.countByStatusAndIsDeletedFalse(ReceiptStatus.DRAFT);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponseDto getReceipt(Long id) {
        Receipt receipt = receiptRepository.findActiveWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + id));
        return mapToResponseDto(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReceiptResponseDto> getReceipts() {
        return receiptRepository.findAllActive()
                .stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<ReceiptResponseDto> getReceiptsPaginated(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receiptDate"));
        Page<Receipt> receiptPage = receiptRepository.findActiveWithSearch(search, pageable);
        return PageResponseDto.from(receiptPage, this::mapToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<ReceiptListItemDto> searchReceipts(ReceiptSearchFilter filter, Pageable pageable) {
        Page<Receipt> receiptPage = receiptRepository.findAll(
                ReceiptSpecification.withFilters(filter),
                pageable);
        return PageResponseDto.from(receiptPage, this::mapToListItemDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptNavigationWindowResponse getNavigationWindow(Long centerReceiptId, int before, int after) {
        int safeBefore = Math.max(before, 0);
        int safeAfter = Math.max(after, 0);

        if (centerReceiptId == null) {
            return getLatestNavigationWindow(safeAfter);
        }

        Receipt centerReceipt = receiptRepository.findActiveWithItemsById(centerReceiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + centerReceiptId));

        List<Receipt> newerReceipts = receiptRepository.findNewerThan(
                centerReceiptId, PageRequest.of(0, safeBefore));
        Collections.reverse(newerReceipts);

        List<Receipt> olderReceipts = receiptRepository.findOlderThan(
                centerReceiptId, PageRequest.of(0, safeAfter));

        List<Receipt> receipts = new ArrayList<>();
        receipts.addAll(newerReceipts);
        receipts.add(centerReceipt);
        receipts.addAll(olderReceipts);

        return ReceiptNavigationWindowResponse.builder()
                .currentReceiptId(centerReceipt.getId())
                .currentIndex(newerReceipts.size())
                .hasOlder(receiptRepository.existsSavedOlderThan(centerReceipt.getId()))
                .hasNewer(receiptRepository.existsSavedNewerThan(centerReceipt.getId()))
                .receipts(receipts.stream().map(this::mapToResponseDto).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptNavigationResponse navigate(Long receiptId, NavigationDirection direction, int limit) {
        Receipt currentReceipt = receiptRepository.findActiveWithItemsById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + receiptId));

        LocalDateTime anchorDate = currentReceipt.getReceiptDate();
        Long anchorId = currentReceipt.getId();
        Pageable pageLimit = PageRequest.of(0, limit);

        List<Receipt> navigatedReceipts;
        boolean hasPrevious;
        boolean hasNext;
        int currentIndex;

        if (direction == NavigationDirection.NEXT) {
            navigatedReceipts = receiptRepository.findNextInOrder(anchorDate, anchorId, pageLimit);

            List<Receipt> combined = new ArrayList<>();
            combined.add(currentReceipt);
            combined.addAll(navigatedReceipts);

            Receipt lastInBatch = combined.getLast();
            hasNext = receiptRepository.existsNextInOrder(lastInBatch.getReceiptDate(), lastInBatch.getId());
            hasPrevious = receiptRepository.existsPreviousInOrder(anchorDate, anchorId);
            currentIndex = 0;

            return ReceiptNavigationResponse.builder()
                    .currentReceiptId(currentReceipt.getId())
                    .currentIndex(currentIndex)
                    .hasPrevious(hasPrevious)
                    .hasNext(hasNext)
                    .receipts(combined.stream().map(this::mapToResponseDto).toList())
                    .build();

        } else {
            navigatedReceipts = receiptRepository.findPreviousInOrder(anchorDate, anchorId, pageLimit);
            Collections.reverse(navigatedReceipts);

            List<Receipt> combined = new ArrayList<>();
            combined.addAll(navigatedReceipts);
            combined.add(currentReceipt);

            currentIndex = combined.size() - 1;

            Receipt firstInBatch = combined.getFirst();
            hasPrevious = receiptRepository.existsPreviousInOrder(firstInBatch.getReceiptDate(), firstInBatch.getId());
            hasNext = receiptRepository.existsNextInOrder(anchorDate, anchorId);

            return ReceiptNavigationResponse.builder()
                    .currentReceiptId(currentReceipt.getId())
                    .currentIndex(currentIndex)
                    .hasPrevious(hasPrevious)
                    .hasNext(hasNext)
                    .receipts(combined.stream().map(this::mapToResponseDto).toList())
                    .build();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RefillValidateResponse validateRefill(RefillValidateRequest request) {
        return cashierProductPort.validateRefill(request);
    }

    @Override
    public com.mgh.backend.product.dto.response.ProductDto executeRefill(RefillExecuteRequest request) {
        return cashierProductPort.executeRefill(request);
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private ReceiptItem buildItemAndDeductStock(ReceiptItemRequest itemReq, Receipt receipt) {
        CashierSaleProduct product = cashierProductPort.deductStock(
                itemReq.getProductCode(),
                itemReq.getQuantity());

        BigDecimal sellingPrice = product.sellingPrice();
        BigDecimal lineTotal = itemReq.getTotal() != null
                ? itemReq.getTotal()
                : sellingPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

        return ReceiptItem.builder()
                .receipt(receipt)
                .productCode(product.barcode())
                .productName(product.name())
                .quantity(itemReq.getQuantity())
                .sellingPrice(sellingPrice)
                .buyingPrice(product.buyingPrice())
                .total(lineTotal)
                .remainingStock(product.remainingStock())
                .build();
    }

    private ReceiptItem buildItemWithoutStockDeduction(ReceiptItemRequest itemReq, Receipt receipt) {
        CashierSaleProduct product = cashierProductPort.getProductSnapshot(itemReq.getProductCode());

        BigDecimal sellingPrice = itemReq.getSellingPrice() != null
                ? itemReq.getSellingPrice()
                : product.sellingPrice();
        BigDecimal lineTotal = itemReq.getTotal() != null
                ? itemReq.getTotal()
                : sellingPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

        return ReceiptItem.builder()
                .receipt(receipt)
                .productCode(product.barcode())
                .productName(product.name())
                .quantity(itemReq.getQuantity())
                .sellingPrice(sellingPrice)
                .buyingPrice(product.buyingPrice())
                .total(lineTotal)
                .remainingStock(product.remainingStock())
                .build();
    }

    private void updateItemWithoutStockDeduction(ReceiptItem item, ReceiptItemRequest itemReq) {
        CashierSaleProduct product = cashierProductPort.getProductSnapshot(itemReq.getProductCode());

        BigDecimal sellingPrice = itemReq.getSellingPrice() != null
                ? itemReq.getSellingPrice()
                : product.sellingPrice();
        BigDecimal lineTotal = itemReq.getTotal() != null
                ? itemReq.getTotal()
                : sellingPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

        item.setProductName(product.name());
        item.setQuantity(itemReq.getQuantity());
        item.setSellingPrice(sellingPrice);
        item.setBuyingPrice(product.buyingPrice());
        item.setTotal(lineTotal);
        item.setRemainingStock(product.remainingStock());
    }

    private void updateItemAndDeductStock(ReceiptItem item, ReceiptItemRequest itemReq) {
        CashierSaleProduct product = cashierProductPort.deductStock(
                itemReq.getProductCode(),
                itemReq.getQuantity());

        BigDecimal sellingPrice = product.sellingPrice();
        BigDecimal lineTotal = itemReq.getTotal() != null
                ? itemReq.getTotal()
                : sellingPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

        item.setProductName(product.name());
        item.setQuantity(itemReq.getQuantity());
        item.setSellingPrice(sellingPrice);
        item.setBuyingPrice(product.buyingPrice());
        item.setTotal(lineTotal);
        item.setRemainingStock(product.remainingStock());
    }

    private void restoreStockForItems(List<ReceiptItem> items) {
        for (ReceiptItem item : items) {
            double restoredQuantity = item.getQuantity();
            Double stockBefore = cashierProductPort.getCurrentStock(item.getProductCode());
            cashierProductPort.restoreStock(item.getProductCode(), restoredQuantity);

            log.debug("Restored stock for product '{}': {} + {} = {}",
                    item.getProductCode(), stockBefore, restoredQuantity,
                    stockBefore != null ? stockBefore + restoredQuantity : restoredQuantity);
        }
    }

    private void recalculateTotals(Receipt receipt) {
        double totalQty = receipt.getItems().stream()
                .mapToDouble(ReceiptItem::getQuantity).sum();
        BigDecimal subtotal = receipt.getItems().stream()
                .map(ReceiptItem::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tax = receipt.getTax() != null ? receipt.getTax() : BigDecimal.ZERO;
        BigDecimal discount = receipt.getDiscount() != null ? receipt.getDiscount() : BigDecimal.ZERO;
        BigDecimal finalTotal = subtotal.add(tax).subtract(discount);

        receipt.setTotalQuantity(totalQty);
        receipt.setTotalItems(receipt.getItems().size());
        receipt.setTotalAmount(finalTotal);
    }

    private String generateUniqueReceiptNumber() {
        String receiptNumber;
        int attempts = 0;
        do {
            receiptNumber = generateReceiptNumber();
            attempts++;
            if (attempts > 10) {
                throw new BusinessException("Failed to generate unique receipt number");
            }
        } while (receiptRepository.existsByReceiptNumber(receiptNumber));
        return receiptNumber;
    }

    private String generateReceiptNumber() {
        String datePart = LocalDateTime.now().toLocalDate().toString().replace("-", "");
        String uuid = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return datePart + "-" + uuid;
    }

    private PaymentMethod parsePaymentMethod(String method) {
        if (method == null || method.isBlank())
            return PaymentMethod.CASH;
        try {
            return PaymentMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid payment method: " + method);
        }
    }

    private ReceiptType parseReceiptType(String type) {
        if (type == null || type.isBlank())
            return ReceiptType.SELL;
        try {
            return ReceiptType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid receipt type: " + type);
        }
    }

    private ReceiptNavigationWindowResponse getLatestNavigationWindow(int after) {
        List<Receipt> receipts = receiptRepository.findLatestNavigationWindow(
                PageRequest.of(0, after + 1));

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
                .hasOlder(receiptRepository.existsSavedOlderThan(latestReceipt.getId()))
                .hasNewer(false)
                .receipts(receipts.stream().map(this::mapToResponseDto).toList())
                .build();
    }

    // ──────────────────────────────────────────────
    // DTO mapping
    // ──────────────────────────────────────────────

    private ReceiptResponseDto mapToResponseDto(Receipt receipt) {
        List<ReceiptItemResponse> itemDtos = receipt.getItems().stream()
                .map(this::mapItemToDto)
                .collect(Collectors.toList());

        BigDecimal subtotal = receipt.getItems().stream()
                .map(ReceiptItem::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tax = receipt.getTax() != null ? receipt.getTax() : BigDecimal.ZERO;
        BigDecimal discount = receipt.getDiscount() != null ? receipt.getDiscount() : BigDecimal.ZERO;
        BigDecimal finalTotal = subtotal.add(tax).subtract(discount);

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
                .customerPhone(receipt.getCustomerPhone())
                .customerId(receipt.getCustomer() != null ? receipt.getCustomer().getId() : null)
                .cashierId(receipt.getCashier() != null ? receipt.getCashier().getId() : null)
                .cashierName(receipt.getCashier() != null && receipt.getCashier().getFullName() != null
                        ? receipt.getCashier().getFullName()
                        : "Unknown")
                .tax(tax)
                .discount(discount)
                .subtotal(subtotal)
                .finalTotal(finalTotal)
                .items(itemDtos)
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }

    private ReceiptItemResponse mapItemToDto(ReceiptItem item) {
        Double currentStock = cashierProductPort.getCurrentStock(item.getProductCode());

        return ReceiptItemResponse.builder()
                .productCode(item.getProductCode())
                .productName(item.getProductName())
                .sellingPrice(item.getSellingPrice())
                .buyingPrice(item.getBuyingPrice())
                .quantity(item.getQuantity())
                .totalPrice(item.getTotal())
                .remainingStock(item.getRemainingStock())
                .currentRemainingStock(currentStock)
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
                .customerPhone(receipt.getCustomerPhone())
                .status(receipt.getStatus())
                .paymentMethod(receipt.getPaymentMethod())
                .cashierName(receipt.getCashier() != null ? receipt.getCashier().getFullName() : null)
                .build();
    }
}
