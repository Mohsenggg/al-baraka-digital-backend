package com.mgh.backend.cashier.printing.renderer;

import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptElement;
import com.mgh.backend.cashier.printing.model.elements.*;
import org.springframework.stereotype.Component;

@Component
public class ReceiptHtmlRenderer {

    public String render(ReceiptDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family: monospace; width: 300px; margin: 0 auto; border: 1px solid #ccc; padding: 10px;'>");
        
        for (ReceiptElement el : doc.getElements()) {
            if (el instanceof HeaderElement header) {
                sb.append("<div style='text-align: center; margin-bottom: 10px;'>");
                sb.append("<h3>").append(header.getStoreName()).append("</h3>");
                sb.append("<div>").append(header.getStoreAddress()).append("</div>");
                sb.append("<div>").append(header.getStorePhone()).append("</div>");
                sb.append("</div>");
                sb.append("<div>Date: ").append(header.getReceiptDate()).append("</div>");
                sb.append("<div>Receipt #: ").append(header.getReceiptNumber()).append("</div>");
                if (header.getCashierName() != null) {
                    sb.append("<div>Cashier: ").append(header.getCashierName()).append("</div>");
                }
            } else if (el instanceof CustomerElement customer) {
                sb.append("<div style='margin-bottom: 10px;'>");
                if (customer.getCustomerName() != null) {
                    sb.append("<div>Customer: ").append(customer.getCustomerName()).append("</div>");
                }
                if (customer.getCustomerPhone() != null) {
                    sb.append("<div>Phone: ").append(customer.getCustomerPhone()).append("</div>");
                }
                sb.append("</div>");
            } else if (el instanceof ItemsTableElement items) {
                sb.append("<table style='width: 100%; text-align: left; border-collapse: collapse; margin-bottom: 10px;'>");
                sb.append("<tr><th>Item</th><th>Qty</th><th>Price</th><th>Total</th></tr>");
                for (ItemsTableElement.ItemRow row : items.getItems()) {
                    sb.append("<tr>");
                    sb.append("<td>").append(row.getName()).append("</td>");
                    sb.append("<td>").append(row.getQuantity()).append("</td>");
                    sb.append("<td>").append(row.getUnitPrice()).append("</td>");
                    sb.append("<td>").append(row.getTotalPrice()).append("</td>");
                    sb.append("</tr>");
                }
                sb.append("</table>");
            } else if (el instanceof TotalsElement totals) {
                sb.append("<div style='text-align: right; margin-bottom: 10px;'>");
                sb.append("<div>Subtotal: ").append(totals.getSubtotal()).append("</div>");
                if (totals.getTaxAmount() != null) {
                    sb.append("<div>Tax: ").append(totals.getTaxAmount()).append("</div>");
                }
                if (totals.getDiscountAmount() != null && !totals.getDiscountAmount().equals("0.00") && !totals.getDiscountAmount().equals("0")) {
                    sb.append("<div>Discount: ").append(totals.getDiscountAmount()).append("</div>");
                }
                sb.append("<h4>Total: ").append(totals.getGrandTotal()).append("</h4>");
                sb.append("</div>");
            } else if (el instanceof SeparatorElement) {
                sb.append("<hr style='border: 1px dashed #000; margin: 10px 0;'/>");
            } else if (el instanceof FooterElement footer) {
                sb.append("<div style='text-align: center; margin-top: 10px;'>");
                sb.append(footer.getText());
                sb.append("</div>");
            }
        }
        
        sb.append("</div>");
        return sb.toString();
    }
}
