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
import java.util.Arrays;

@Component
public class ReceiptEscPosRenderer {

    // 80mm printers typically support 48 characters per line (Font A: 12x24, 576 dots)
    private static final int LINE_WIDTH = 48; 
    
    // Widths for columns: Item(22), Qty(8), Price(9), Total(9)
    private static final int COL_TOTAL_WIDTH = 9;
    private static final int COL_PRICE_WIDTH = 9;
    private static final int COL_QTY_WIDTH = 8;
    private static final int COL_ITEM_WIDTH = 22;

    public byte[] render(ReceiptDocument doc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EscPos escpos = new EscPos(baos);

        // Configure encoding for Arabic
        // Note: Full RTL shaping depends on printer firmware. If the printer prints disconnected Arabic,
        // an external shaping library or image-based printing (Graphics) is required.
        // For Xprinters, Arabic is often CP864 (Code table 40) or Windows-1256 (Code table 22).
        // Let's use the built-in WPC1256_Arabic if available, or just set by value.
        escpos.setCharsetName("windows-1256");
        // Sending ESC t 22 (code page 22 for windows-1256 on many Xprinters)
        escpos.write(Arrays.toString(new byte[]{0x1B, 0x74, 22}));

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
                if (header.getStoreName() != null) {
                    escpos.writeLF(titleStyle, header.getStoreName());
                }
                escpos.feed(1);
                
                // Print right-aligned fields as shown in the reference
                printRightAlignedField(escpos, rightStyle, "الفرع        : ", header.getBranch());
                printRightAlignedField(escpos, rightStyle, "التليفون     : ", header.getPhone());
                printRightAlignedField(escpos, rightStyle, "العنوان      : ", header.getAddress());
                printRightAlignedField(escpos, rightStyle, "المستخدم     : ", header.getUser());
                
                escpos.feed(1);
                if (header.getReceiptNumber() != null) {
                    // Barcode in center
                    BarCode barcode = new BarCode();
                    barcode.setSystem(BarCode.BarCodeSystem.CODE128);
                    barcode.setJustification(EscPosConst.Justification.Center);
                    // CODE128 requires a charset prefix in escpos-coffee (e.g. {B)
                    escpos.write(barcode, "{B" + header.getReceiptNumber());
                    escpos.feed(1);
                }

                printRightAlignedField(escpos, rightStyle, "البائع       : ", header.getSeller());
                printRightAlignedField(escpos, rightStyle, "بون رقم      : ", header.getReceiptNumber());
                printRightAlignedField(escpos, rightStyle, "التاريخ      : ", header.getDate());
                
            } else if (el instanceof ItemsTableElement items) {
                // Print headers (Total | Price | Qty | Item)
                String headerRow = padRight("الإجمالي", COL_TOTAL_WIDTH) +
                                   padRight("السعر", COL_PRICE_WIDTH) +
                                   padRight("الكمية", COL_QTY_WIDTH) +
                                   padLeft("الصنف", COL_ITEM_WIDTH);
                escpos.writeLF(boldStyle, headerRow);
                
                for (ItemsTableElement.ItemRow row : items.getItems()) {
                    String name = row.getName() != null ? row.getName() : "";
                    if (name.length() > COL_ITEM_WIDTH) {
                        name = name.substring(0, COL_ITEM_WIDTH); // Truncate safely
                    }
                    
                    String itemRow = padRight(row.getTotalPrice(), COL_TOTAL_WIDTH) +
                                     padRight(row.getUnitPrice(), COL_PRICE_WIDTH) +
                                     padRight(row.getQuantity(), COL_QTY_WIDTH) +
                                     padLeft(name, COL_ITEM_WIDTH);
                    escpos.writeLF(leftStyle, itemRow);
                }
            } else if (el instanceof TotalsElement totals) {
                escpos.feed(1);
                
                // "إجمالي قطع : 2             140.00"
                String totalItemsLine = padRight(totals.getTotalItemsAmount(), COL_TOTAL_WIDTH + COL_PRICE_WIDTH) +
                                        padLeft("إجمالي قطع : " + totals.getTotalItemsCount(), LINE_WIDTH - (COL_TOTAL_WIDTH + COL_PRICE_WIDTH));
                escpos.writeLF(boldStyle, totalItemsLine);
                
                escpos.writeLF(leftStyle, "------------------------------------------------");
                
                printRightAlignedField(escpos, rightStyle, "خصم الفاتورة : ", totals.getDiscountAmount());
                printRightAlignedField(escpos, rightStyle, "صافي الفاتورة: ", totals.getNetTotal());
                printRightAlignedField(escpos, rightStyle, "المدفوع     : ", totals.getPaidAmount());
                printRightAlignedField(escpos, rightStyle, "الباقي      : ", totals.getRemainingAmount());
                
            } else if (el instanceof SeparatorElement) {
                // 48 dashes for 80mm printer
                escpos.writeLF(leftStyle, "------------------------------------------------"); 
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
}
