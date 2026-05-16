package com.mgh.backend.cashier.repository;

import com.mgh.backend.cashier.entity.ReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, Long> {

    Optional<ReceiptItem> findByReceiptIdAndId(Long invoiceId, Long itemId);

    Optional<ReceiptItem> findByReceiptIdAndProductCode(Long receiptId, String productCode);
}