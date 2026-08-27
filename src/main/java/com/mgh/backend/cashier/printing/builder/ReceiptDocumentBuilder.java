package com.mgh.backend.cashier.printing.builder;

import com.mgh.backend.cashier.entity.Cashier;
import com.mgh.backend.cashier.entity.Receipt;
import com.mgh.backend.cashier.entity.ReceiptItem;
import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptLayoutConfig;
import com.mgh.backend.cashier.printing.model.elements.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ReceiptDocumentBuilder {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    public ReceiptDocument build(Receipt receipt, ReceiptLayoutConfig config) {
        if (config == null) {
            config = ReceiptLayoutConfig.builder().build();
        }

        ReceiptDocument doc = new ReceiptDocument();

        // 1. Header
        Cashier cashier = receipt.getCashier();
        String userStr = "";
        String sellerStr = "";
        if (config.isShowCashier() && cashier != null) {
            String fullName = cashier.getFullName() != null ? cashier.getFullName() : "";
            userStr = cashier.getId() != null ? cashier.getId() + " - " + fullName : fullName;
            sellerStr = cashier.getUsername() != null && !cashier.getUsername().isBlank()
                    ? cashier.getUsername()
                    : fullName;
        }

        String dateStr = "";
        String timeStr = "";
        if (receipt.getReceiptDate() != null) {
            dateStr = receipt.getReceiptDate().format(DATE_FORMATTER);
            timeStr = receipt.getReceiptDate().format(TIME_FORMATTER).toLowerCase(Locale.ENGLISH);
        }

        HeaderElement header = HeaderElement.builder()
                .storeName("البركة للمنظفات")
                .branch("البركة للمنظفات")
                .phone("01065527862")
                .address("شارع الكيلانى")
                .user(userStr)
                .seller(sellerStr)
                .receiptNumber(receipt.getReceiptNumber() != null ? receipt.getReceiptNumber() : "")
                .date(dateStr)
                .time(timeStr)
                .build();
        doc.addElement(header);
        doc.addElement(new SeparatorElement());

        // 2. Items & Calculations
        List<ItemsTableElement.ItemRow> itemRows = new ArrayList<>();
        double sumQuantity = 0.0;
        BigDecimal calculatedSubtotal = BigDecimal.ZERO;

        if (receipt.getItems() != null) {
            for (ReceiptItem item : receipt.getItems()) {
                double qty = item.getQuantity() != null ? item.getQuantity() : 0.0;
                sumQuantity += qty;

                BigDecimal unitPrice = item.getSellingPrice() != null ? item.getSellingPrice() : BigDecimal.ZERO;
                BigDecimal lineTotal = item.getTotal() != null
                        ? item.getTotal()
                        : unitPrice.multiply(BigDecimal.valueOf(qty));
                calculatedSubtotal = calculatedSubtotal.add(lineTotal);

                itemRows.add(ItemsTableElement.ItemRow.builder()
                        .name(item.getProductName() != null ? item.getProductName() : "")
                        .quantity(formatQuantity(qty))
                        .unitPrice(formatMoney(unitPrice))
                        .totalPrice(formatMoney(lineTotal))
                        .build());
            }
        }
        doc.addElement(new ItemsTableElement(itemRows));
        doc.addElement(new SeparatorElement());

        // 3. Totals
        BigDecimal discount = receipt.getDiscount() != null ? receipt.getDiscount() : BigDecimal.ZERO;
        BigDecimal tax = receipt.getTax() != null ? receipt.getTax() : BigDecimal.ZERO;
        BigDecimal netTotal = receipt.getTotalAmount() != null
                ? receipt.getTotalAmount()
                : calculatedSubtotal.add(tax).subtract(discount);

        TotalsElement totals = TotalsElement.builder()
                .totalItemsCount(formatQuantity(sumQuantity))
                .totalItemsAmount(formatMoney(calculatedSubtotal))
                .discountAmount(formatMoney(discount))
                .netTotal(formatMoney(netTotal))
                .paidAmount(formatMoney(BigDecimal.ZERO))
                .remainingAmount(formatMoney(BigDecimal.ZERO))
                .build();
        doc.addElement(totals);
        doc.addElement(new SeparatorElement());

        // 4. Footer
        String footerText = config.getFooterText() != null && !config.getFooterText().isBlank()
                ? config.getFooterText()
                : "يسعدنا زيارتكم لنا بالمحل...";
        doc.addElement(new FooterElement(footerText));

        return doc;
    }

    private String formatQuantity(double qty) {
        if (qty == Math.floor(qty) && !Double.isInfinite(qty)) {
            return String.valueOf((long) qty);
        }
        return String.format(Locale.US, "%.2f", qty);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return String.format(Locale.US, "%.2f", amount);
    }
}
