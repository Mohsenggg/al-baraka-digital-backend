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
                .storeName("AL-Baraka Pos")
                .storeAddress("123 Main Street")
                .storePhone("555-0199")
                .receiptDate(receipt.getReceiptDate() != null ? receipt.getReceiptDate().format(DATE_FORMATTER) : "")
                .receiptNumber(receipt.getReceiptNumber())
                .cashierName(config.isShowCashier() && receipt.getCashier() != null ? receipt.getCashier().getFullName() : null)
                .build();
        doc.addElement(header);
        doc.addElement(new SeparatorElement());

        // 2. Customer
        if (receipt.getCustomerName() != null || receipt.getCustomerPhone() != null) {
            CustomerElement customer = CustomerElement.builder()
                    .customerName(receipt.getCustomerName())
                    .customerPhone(config.isShowCustomerPhone() ? receipt.getCustomerPhone() : null)
                    .build();
            doc.addElement(customer);
            doc.addElement(new SeparatorElement());
        }

        // 3. Items
        List<ItemsTableElement.ItemRow> itemRows = new ArrayList<>();
        if (receipt.getItems() != null) {
            for (ReceiptItem item : receipt.getItems()) {
                itemRows.add(ItemsTableElement.ItemRow.builder()
                        .name(item.getProductName())
                        .quantity(String.valueOf(item.getQuantity()))
                        .unitPrice(item.getSellingPrice() != null ? item.getSellingPrice().toString() : "0.00")
                        .totalPrice(item.getTotal() != null ? item.getTotal().toString() : "0.00")
                        .build());
            }
        }
        doc.addElement(new ItemsTableElement(itemRows));
        doc.addElement(new SeparatorElement());

        // 4. Totals
        TotalsElement totals = TotalsElement.builder()
                .subtotal(receipt.getTotalAmount() != null ? receipt.getTotalAmount().toString() : "0.00")
                .taxAmount(config.isShowTaxDetails() && receipt.getTax() != null ? receipt.getTax().toString() : null)
                .discountAmount(receipt.getDiscount() != null ? receipt.getDiscount().toString() : "0.00")
                .grandTotal(receipt.getTotalAmount() != null ? receipt.getTotalAmount().toString() : "0.00")
                .build();
        doc.addElement(totals);
        doc.addElement(new SeparatorElement());

        // 5. Footer
        if (config.getFooterText() != null && !config.getFooterText().isEmpty()) {
            doc.addElement(new FooterElement(config.getFooterText()));
        }

        return doc;
    }
}
