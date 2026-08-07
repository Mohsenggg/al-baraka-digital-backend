package com.mgh.backend.cashier.printing.builder;

import com.mgh.backend.cashier.entity.Receipt;
import com.mgh.backend.cashier.entity.ReceiptItem;
import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptLayoutConfig;
import com.mgh.backend.cashier.printing.model.elements.*;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReceiptDocumentBuilder {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ReceiptDocument build(Receipt receipt, ReceiptLayoutConfig config) {
        ReceiptDocument doc = new ReceiptDocument();

        // 1. Header
        HeaderElement header = HeaderElement.builder()
                .storeName("البركة للمنظفات") // Should ideally come from config or store settings
                .branch("البركة للمنظفات")
                .phone("01065527862") // Usually from config
                .address("شارع الكيلانى")
                .user(config.isShowCashier() && receipt.getCashier() != null ? receipt.getCashier().getFullName() : "") // "المستخدم"
                .seller(receipt.getCashier() != null ? receipt.getCashier().getUsername() : "") // "البائع"
                .receiptNumber(receipt.getReceiptNumber())
                .date(receipt.getReceiptDate() != null ? receipt.getReceiptDate().format(DATE_FORMATTER) : "")
                .build();
        doc.addElement(header);
        doc.addElement(new SeparatorElement());

        // 2. Items
        List<ItemsTableElement.ItemRow> itemRows = new ArrayList<>();
        if (receipt.getItems() != null) {
            for (ReceiptItem item : receipt.getItems()) {
                itemRows.add(ItemsTableElement.ItemRow.builder()
                        .name(item.getProductName())
                        .quantity(String.valueOf(item.getQuantity().intValue())) // Assume integer quantities for display if applicable, but format properly
                        .unitPrice(item.getSellingPrice() != null ? item.getSellingPrice().toString() : "0.00")
                        .totalPrice(item.getTotal() != null ? item.getTotal().toString() : "0.00")
                        .build());
            }
        }
        doc.addElement(new ItemsTableElement(itemRows));
        doc.addElement(new SeparatorElement());

        // 3. Totals
        TotalsElement totals = TotalsElement.builder()
                .totalItemsCount(receipt.getTotalItems() != null ? String.valueOf(receipt.getTotalItems()) : "0")
                .totalItemsAmount(receipt.getTotalAmount() != null ? receipt.getTotalAmount().toString() : "0.00") // From image, sum of items
                .discountAmount(receipt.getDiscount() != null ? receipt.getDiscount().toString() : "0.00")
                .netTotal(receipt.getTotalAmount() != null ? receipt.getTotalAmount().toString() : "0.00") // Net total after discount
                .paidAmount("0.00") // Usually mapped from payment info if available
                .remainingAmount("0.00") // Usually mapped from payment info if available
                .build();
        doc.addElement(totals);
        doc.addElement(new SeparatorElement());

        // 4. Footer
        String footerText = config.getFooterText() != null && !config.getFooterText().isEmpty() ? config.getFooterText() : "يسعدنا زيارتكم لنا بالمحل...";
        doc.addElement(new FooterElement(footerText));

        return doc;
    }
}
