package com.mgh.backend.cashier.printing.renderer;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptElement;
import com.mgh.backend.cashier.printing.model.elements.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ReceiptEscPosRenderer {

    public byte[] render(ReceiptDocument doc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EscPos escpos = new EscPos(baos);

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
                escpos.writeLF(titleStyle, header.getStoreName() != null ? header.getStoreName() : "");
                escpos.writeLF(centerStyle, header.getStoreAddress() != null ? header.getStoreAddress() : "");
                escpos.writeLF(centerStyle, header.getStorePhone() != null ? header.getStorePhone() : "");
                escpos.feed(1);
                
                escpos.writeLF(leftStyle, "Date: " + header.getReceiptDate());
                escpos.writeLF(leftStyle, "Receipt #: " + header.getReceiptNumber());
                if (header.getCashierName() != null) {
                    escpos.writeLF(leftStyle, "Cashier: " + header.getCashierName());
                }
            } else if (el instanceof CustomerElement customer) {
                if (customer.getCustomerName() != null) {
                    escpos.writeLF(leftStyle, "Customer: " + customer.getCustomerName());
                }
                if (customer.getCustomerPhone() != null) {
                    escpos.writeLF(leftStyle, "Phone: " + customer.getCustomerPhone());
                }
            } else if (el instanceof ItemsTableElement items) {
                escpos.writeLF(boldStyle, "Item                 Qty  Price  Total");
                for (ItemsTableElement.ItemRow row : items.getItems()) {
                    String name = row.getName();
                    if (name != null && name.length() > 20) name = name.substring(0, 20);
                    else if (name == null) name = "";
                    String paddedName = String.format("%-20s", name);
                    String qty = String.format("%-4s", row.getQuantity());
                    String price = String.format("%-6s", row.getUnitPrice());
                    String total = String.format("%-6s", row.getTotalPrice());
                    escpos.writeLF(leftStyle, paddedName + " " + qty + " " + price + " " + total);
                }
            } else if (el instanceof TotalsElement totals) {
                escpos.feed(1);
                escpos.writeLF(rightStyle, "Subtotal: " + totals.getSubtotal());
                if (totals.getTaxAmount() != null) {
                    escpos.writeLF(rightStyle, "Tax: " + totals.getTaxAmount());
                }
                if (totals.getDiscountAmount() != null && !totals.getDiscountAmount().equals("0.00") && !totals.getDiscountAmount().equals("0")) {
                    escpos.writeLF(rightStyle, "Discount: " + totals.getDiscountAmount());
                }
                escpos.writeLF(new Style().setJustification(EscPosConst.Justification.Right).setBold(true), 
                        "Total: " + totals.getGrandTotal());
            } else if (el instanceof SeparatorElement) {
                escpos.writeLF(leftStyle, "--------------------------------"); // 32 chars for 58mm printer
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
}
