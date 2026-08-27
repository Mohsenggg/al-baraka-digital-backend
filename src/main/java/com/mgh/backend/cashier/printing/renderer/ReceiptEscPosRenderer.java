package com.mgh.backend.cashier.printing.renderer;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.escpos.barcode.BarCode;
import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptElement;
import com.mgh.backend.cashier.printing.model.elements.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ReceiptEscPosRenderer {

    // 80mm thermal printers support 48 characters per line (Font A)
    private static final int LINE_WIDTH = 48;

    // Column widths for 48-char line: Total(9), Price(9), Qty(8), Item(22)
    private static final int COL_TOTAL_WIDTH = 9;
    private static final int COL_PRICE_WIDTH = 9;
    private static final int COL_QTY_WIDTH = 8;
    private static final int COL_ITEM_WIDTH = 22;

    private static final String DASHED_LINE = "------------------------------------------------";
    private static final String DOUBLE_LINE = "================================================";

    public byte[] render(ReceiptDocument doc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 1. Initialize printer and send raw code page switch command
        // ESC @ (Initialize)
        baos.write(new byte[]{0x1B, 0x40});
        // ESC t 22 (Code table 22: Windows-1256 on Xprinter XP-D200N and compatible 80mm printers)
        baos.write(new byte[]{0x1B, 0x74, 22});

        EscPos escpos = new EscPos(baos);
        escpos.setCharsetName("windows-1256");

        Style titleStyle = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style centerStyle = new Style().setJustification(EscPosConst.Justification.Center);
        Style leftStyle = new Style().setJustification(EscPosConst.Justification.Left_Default);
        Style rightStyle = new Style().setJustification(EscPosConst.Justification.Right);
        Style boldStyle = new Style().setBold(true);

        for (ReceiptElement el : doc.getElements()) {
            if (el instanceof HeaderElement header) {
                if (header.getStoreName() != null && !header.getStoreName().isBlank()) {
                    escpos.writeLF(titleStyle, header.getStoreName());
                }
                escpos.feed(1);

                // Right-aligned meta fields
                printRightAlignedField(escpos, rightStyle, "الفرع        : ", header.getBranch());
                printRightAlignedField(escpos, rightStyle, "التليفون     : ", header.getPhone());
                printRightAlignedField(escpos, rightStyle, "العنوان      : ", header.getAddress());
                printRightAlignedField(escpos, rightStyle, "المستخدم     : ", header.getUser());

                // Barcode for receipt number (centered)
                if (header.getReceiptNumber() != null && !header.getReceiptNumber().isBlank()) {
                    escpos.feed(1);
                    BarCode barcode = new BarCode();
                    barcode.setSystem(BarCode.BarCodeSystem.CODE128);
                    barcode.setJustification(EscPosConst.Justification.Center);
                    escpos.write(barcode, "{B" + header.getReceiptNumber());
                    escpos.feed(1);
                }

                printRightAlignedField(escpos, rightStyle, "البائع       : ", header.getSeller());
                printRightAlignedField(escpos, rightStyle, "بون رقم      : ", header.getReceiptNumber());

                // Date and time line (Time on left, Date in middle, Label on right)
                String timeStr = header.getTime() != null ? header.getTime() : "";
                String dateStr = header.getDate() != null ? header.getDate() : "";
                String dateTimeLine = padRight(timeStr, 12) + padCenter(dateStr, 16) + padLeft("التاريخ      : ", 20);
                escpos.writeLF(leftStyle, dateTimeLine);

            } else if (el instanceof ItemsTableElement items) {
                // Table Headers (Total | Price | Qty | Item)
                String headerRow = padRight("الإجمالي", COL_TOTAL_WIDTH) +
                                   padRight("السعر", COL_PRICE_WIDTH) +
                                   padRight("الكمية", COL_QTY_WIDTH) +
                                   padLeft("الصنف", COL_ITEM_WIDTH);
                escpos.writeLF(boldStyle, headerRow);

                for (ItemsTableElement.ItemRow row : items.getItems()) {
                    String name = row.getName() != null ? row.getName() : "";
                    if (name.length() > COL_ITEM_WIDTH) {
                        name = name.substring(0, COL_ITEM_WIDTH);
                    }

                    String itemRow = padRight(row.getTotalPrice(), COL_TOTAL_WIDTH) +
                                     padRight(row.getUnitPrice(), COL_PRICE_WIDTH) +
                                     padRight(row.getQuantity(), COL_QTY_WIDTH) +
                                     padLeft(name, COL_ITEM_WIDTH);
                    escpos.writeLF(leftStyle, itemRow);
                }

            } else if (el instanceof TotalsElement totals) {
                // Total pieces line: Amount on left, Quantity in middle, Label on right
                String totalPiecesLine = padRight(totals.getTotalItemsAmount(), 12) +
                                        padCenter(totals.getTotalItemsCount(), 12) +
                                        padLeft("إجمالي قطع : ", 24);
                escpos.writeLF(boldStyle, totalPiecesLine);

                // Double line separator
                escpos.writeLF(leftStyle, DOUBLE_LINE);

                printRightAlignedField(escpos, rightStyle, "خصم الفاتورة : ", totals.getDiscountAmount());
                escpos.writeLF(leftStyle, DASHED_LINE);

                printRightAlignedField(escpos, rightStyle, "صافي الفاتورة: ", totals.getNetTotal());
                escpos.writeLF(leftStyle, DASHED_LINE);

                printRightAlignedField(escpos, rightStyle, "المدفوع      : ", totals.getPaidAmount());
                escpos.writeLF(leftStyle, DASHED_LINE);

                printRightAlignedField(escpos, rightStyle, "الباقي       : ", totals.getRemainingAmount());
                escpos.writeLF(leftStyle, DOUBLE_LINE);

            } else if (el instanceof SeparatorElement) {
                escpos.writeLF(leftStyle, DASHED_LINE);

            } else if (el instanceof FooterElement footer) {
                escpos.feed(1);
                escpos.writeLF(centerStyle, footer.getText());
            }
        }

        escpos.feed(4);
        escpos.cut(EscPos.CutMode.FULL);
        escpos.close();

        return baos.toByteArray();
    }

    private void printRightAlignedField(EscPos escpos, Style style, String label, String value) throws IOException {
        String safeValue = value != null ? value : "";
        String line = safeValue + "  " + label;
        escpos.writeLF(style, line);
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        return String.format("%-" + n + "s", s);
    }

    private String padLeft(String s, int n) {
        if (s == null) s = "";
        return String.format("%" + n + "s", s);
    }

    private String padCenter(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s;
        int leftPadding = (n - s.length()) / 2;
        int rightPadding = n - s.length() - leftPadding;
        return " ".repeat(leftPadding) + s + " ".repeat(rightPadding);
    }
}
