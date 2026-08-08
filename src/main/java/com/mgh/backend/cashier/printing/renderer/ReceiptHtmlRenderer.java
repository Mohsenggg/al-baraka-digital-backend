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
                sb.append("<div>الفرع: ").append(header.getBranch()).append("</div>");
                sb.append("<div>التليفون: ").append(header.getPhone()).append("</div>");
                sb.append("<div>العنوان: ").append(header.getAddress()).append("</div>");
                sb.append("<div>المستخدم: ").append(header.getUser()).append("</div>");
                sb.append("</div>");
                sb.append("<div>البائع: ").append(header.getSeller()).append("</div>");
                sb.append("<div>بون رقم: ").append(header.getReceiptNumber()).append("</div>");
                sb.append("<div>التاريخ: ").append(header.getDate()).append("</div>");
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
                sb.append("<div>إجمالي قطع : ").append(totals.getTotalItemsCount()).append("             ").append(totals.getTotalItemsAmount()).append("</div>");
                sb.append("<div>خصم الفاتورة: ").append(totals.getDiscountAmount()).append("</div>");
                sb.append("<h4>صافي الفاتورة: ").append(totals.getNetTotal()).append("</h4>");
                sb.append("<div>المدفوع: ").append(totals.getPaidAmount()).append("</div>");
                sb.append("<div>الباقي: ").append(totals.getRemainingAmount()).append("</div>");
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
