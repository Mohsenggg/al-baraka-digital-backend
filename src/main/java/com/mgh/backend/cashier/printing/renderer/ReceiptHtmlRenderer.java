package com.mgh.backend.cashier.printing.renderer;

import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptElement;
import com.mgh.backend.cashier.printing.model.elements.*;
import org.springframework.stereotype.Component;

@Component
public class ReceiptHtmlRenderer {

    public String render(ReceiptDocument doc) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html dir=\"rtl\" lang=\"ar\">\n");
        sb.append("<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>معاينة الفاتورة - 80mm</title>\n");
        sb.append("<style>\n");
        sb.append("  @page {\n");
        sb.append("    size: 80mm auto;\n");
        sb.append("    margin: 0;\n");
        sb.append("  }\n");
        sb.append("  * {\n");
        sb.append("    box-sizing: border-box;\n");
        sb.append("    margin: 0;\n");
        sb.append("    padding: 0;\n");
        sb.append("  }\n");
        sb.append("  body {\n");
        sb.append("    background-color: #f1f5f9;\n");
        sb.append("    font-family: 'Segoe UI', 'Cairo', -apple-system, BlinkMacSystemFont, Tahoma, Arial, sans-serif;\n");
        sb.append("    display: flex;\n");
        sb.append("    flex-direction: column;\n");
        sb.append("    align-items: center;\n");
        sb.append("    padding: 24px 0;\n");
        sb.append("    direction: rtl;\n");
        sb.append("    color: #000000;\n");
        sb.append("    -webkit-print-color-adjust: exact;\n");
        sb.append("    print-color-adjust: exact;\n");
        sb.append("  }\n");
        sb.append("  .no-print-toolbar {\n");
        sb.append("    margin-bottom: 16px;\n");
        sb.append("    display: flex;\n");
        sb.append("    gap: 12px;\n");
        sb.append("  }\n");
        sb.append("  .print-action-btn {\n");
        sb.append("    background-color: #0f172a;\n");
        sb.append("    color: #ffffff;\n");
        sb.append("    border: none;\n");
        sb.append("    border-radius: 6px;\n");
        sb.append("    padding: 8px 20px;\n");
        sb.append("    font-size: 14px;\n");
        sb.append("    font-weight: bold;\n");
        sb.append("    cursor: pointer;\n");
        sb.append("    box-shadow: 0 2px 4px rgba(0,0,0,0.15);\n");
        sb.append("    display: flex;\n");
        sb.append("    align-items: center;\n");
        sb.append("    gap: 6px;\n");
        sb.append("  }\n");
        sb.append("  .print-action-btn:hover {\n");
        sb.append("    background-color: #1e293b;\n");
        sb.append("  }\n");
        sb.append("  .receipt-paper {\n");
        sb.append("    width: 80mm;\n");
        sb.append("    max-width: 80mm;\n");
        sb.append("    min-width: 80mm;\n");
        sb.append("    background: #ffffff;\n");
        sb.append("    padding: 12px 14px;\n");
        sb.append("    box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);\n");
        sb.append("    font-size: 13px;\n");
        sb.append("    line-height: 1.4;\n");
        sb.append("    color: #000000;\n");
        sb.append("  }\n");
        sb.append("  .store-title {\n");
        sb.append("    text-align: center;\n");
        sb.append("    font-size: 20px;\n");
        sb.append("    font-weight: 900;\n");
        sb.append("    margin-bottom: 10px;\n");
        sb.append("    letter-spacing: 0.5px;\n");
        sb.append("  }\n");
        sb.append("  .meta-table {\n");
        sb.append("    width: 100%;\n");
        sb.append("    border-collapse: collapse;\n");
        sb.append("    font-size: 12.5px;\n");
        sb.append("    margin-bottom: 4px;\n");
        sb.append("  }\n");
        sb.append("  .meta-table td {\n");
        sb.append("    padding: 1.5px 0;\n");
        sb.append("    vertical-align: middle;\n");
        sb.append("  }\n");
        sb.append("  .meta-label {\n");
        sb.append("    width: 75px;\n");
        sb.append("    font-weight: bold;\n");
        sb.append("    text-align: right;\n");
        sb.append("    white-space: nowrap;\n");
        sb.append("  }\n");
        sb.append("  .meta-colon {\n");
        sb.append("    width: 12px;\n");
        sb.append("    text-align: center;\n");
        sb.append("    font-weight: bold;\n");
        sb.append("  }\n");
        sb.append("  .meta-val {\n");
        sb.append("    text-align: right;\n");
        sb.append("    padding-right: 4px;\n");
        sb.append("  }\n");
        sb.append("  .date-time-row {\n");
        sb.append("    display: flex;\n");
        sb.append("    justify-content: space-between;\n");
        sb.append("    align-items: center;\n");
        sb.append("    width: 100%;\n");
        sb.append("  }\n");
        sb.append("  .barcode-container {\n");
        sb.append("    text-align: center;\n");
        sb.append("    margin: 8px 0;\n");
        sb.append("  }\n");
        sb.append("  .barcode-svg {\n");
        sb.append("    height: 38px;\n");
        sb.append("    max-width: 85%;\n");
        sb.append("  }\n");
        sb.append("  .dashed-divider {\n");
        sb.append("    border: none;\n");
        sb.append("    border-top: 1px dashed #000000;\n");
        sb.append("    margin: 6px 0;\n");
        sb.append("    width: 100%;\n");
        sb.append("  }\n");
        sb.append("  .solid-divider {\n");
        sb.append("    border: none;\n");
        sb.append("    border-top: 1px solid #000000;\n");
        sb.append("    margin: 4px 0;\n");
        sb.append("    width: 100%;\n");
        sb.append("  }\n");
        sb.append("  .double-divider {\n");
        sb.append("    border: none;\n");
        sb.append("    border-top: 3px double #000000;\n");
        sb.append("    margin: 4px 0;\n");
        sb.append("    width: 100%;\n");
        sb.append("  }\n");
        sb.append("  .items-table {\n");
        sb.append("    width: 100%;\n");
        sb.append("    border-collapse: collapse;\n");
        sb.append("    font-size: 12px;\n");
        sb.append("    margin: 2px 0;\n");
        sb.append("  }\n");
        sb.append("  .items-table th {\n");
        sb.append("    font-weight: bold;\n");
        sb.append("    padding: 3px 0;\n");
        sb.append("    border: none;\n");
        sb.append("  }\n");
        sb.append("  .items-table td {\n");
        sb.append("    padding: 3px 0;\n");
        sb.append("    vertical-align: top;\n");
        sb.append("    border: none;\n");
        sb.append("  }\n");
        sb.append("  .col-item {\n");
        sb.append("    text-align: right;\n");
        sb.append("    width: 48%;\n");
        sb.append("    word-break: break-word;\n");
        sb.append("  }\n");
        sb.append("  .col-qty {\n");
        sb.append("    text-align: center;\n");
        sb.append("    width: 14%;\n");
        sb.append("  }\n");
        sb.append("  .col-price {\n");
        sb.append("    text-align: left;\n");
        sb.append("    width: 18%;\n");
        sb.append("    direction: ltr;\n");
        sb.append("  }\n");
        sb.append("  .col-total {\n");
        sb.append("    text-align: left;\n");
        sb.append("    width: 20%;\n");
        sb.append("    direction: ltr;\n");
        sb.append("  }\n");
        sb.append("  .totals-block {\n");
        sb.append("    width: 100%;\n");
        sb.append("  }\n");
        sb.append("  .total-pieces-line {\n");
        sb.append("    display: flex;\n");
        sb.append("    justify-content: space-between;\n");
        sb.append("    align-items: center;\n");
        sb.append("    font-weight: bold;\n");
        sb.append("    font-size: 13.5px;\n");
        sb.append("    padding: 3px 0;\n");
        sb.append("  }\n");
        sb.append("  .total-pieces-line .label-part {\n");
        sb.append("    white-space: nowrap;\n");
        sb.append("  }\n");
        sb.append("  .total-pieces-line .qty-part {\n");
        sb.append("    flex-grow: 1;\n");
        sb.append("    text-align: center;\n");
        sb.append("  }\n");
        sb.append("  .total-pieces-line .amt-part {\n");
        sb.append("    text-align: left;\n");
        sb.append("    direction: ltr;\n");
        sb.append("  }\n");
        sb.append("  .totals-row {\n");
        sb.append("    display: flex;\n");
        sb.append("    justify-content: space-between;\n");
        sb.append("    align-items: center;\n");
        sb.append("    padding: 2.5px 0;\n");
        sb.append("    font-size: 12.5px;\n");
        sb.append("  }\n");
        sb.append("  .totals-row .tot-label {\n");
        sb.append("    font-weight: bold;\n");
        sb.append("  }\n");
        sb.append("  .totals-row .tot-value {\n");
        sb.append("    text-align: left;\n");
        sb.append("    direction: ltr;\n");
        sb.append("  }\n");
        sb.append("  .receipt-footer {\n");
        sb.append("    text-align: center;\n");
        sb.append("    margin-top: 10px;\n");
        sb.append("    font-size: 13px;\n");
        sb.append("    font-weight: 500;\n");
        sb.append("  }\n");
        sb.append("  @media print {\n");
        sb.append("    body {\n");
        sb.append("      background: none;\n");
        sb.append("      padding: 0;\n");
        sb.append("      margin: 0;\n");
        sb.append("    }\n");
        sb.append("    .no-print-toolbar {\n");
        sb.append("      display: none !important;\n");
        sb.append("    }\n");
        sb.append("    .receipt-paper {\n");
        sb.append("      box-shadow: none;\n");
        sb.append("      padding: 2mm 3mm;\n");
        sb.append("      width: 100%;\n");
        sb.append("      max-width: 100%;\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("</style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");

        sb.append("<div class=\"no-print-toolbar\">\n");
        sb.append("  <button class=\"print-action-btn\" onclick=\"window.print()\">\n");
        sb.append("    <span>&#128438;</span> طباعة الفاتورة\n");
        sb.append("  </button>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"receipt-paper\">\n");

        for (ReceiptElement el : doc.getElements()) {
            if (el instanceof HeaderElement header) {
                if (header.getStoreName() != null && !header.getStoreName().isBlank()) {
                    sb.append("<div class=\"store-title\">").append(escapeHtml(header.getStoreName())).append("</div>\n");
                }

                sb.append("<table class=\"meta-table\">\n");
                renderMetaRow(sb, "الفرع", header.getBranch());
                renderMetaRow(sb, "التليفون", header.getPhone());
                renderMetaRow(sb, "العنوان", header.getAddress());
                renderMetaRow(sb, "المستخدم", header.getUser());
                sb.append("</table>\n");

                if (header.getReceiptNumber() != null && !header.getReceiptNumber().isBlank()) {
                    sb.append("<div class=\"barcode-container\">\n");
                    sb.append(generateBarcodeSvg(header.getReceiptNumber()));
                    sb.append("</div>\n");
                }

                sb.append("<table class=\"meta-table\">\n");
                renderMetaRow(sb, "البائع", header.getSeller());
                renderMetaRow(sb, "بون رقم", header.getReceiptNumber());

                // Date and Time Row
                sb.append("<tr>\n");
                sb.append("  <td class=\"meta-label\">التاريخ</td>\n");
                sb.append("  <td class=\"meta-colon\">:</td>\n");
                sb.append("  <td class=\"meta-val\">\n");
                sb.append("    <div class=\"date-time-row\">\n");
                sb.append("      <span>").append(escapeHtml(header.getDate() != null ? header.getDate() : "")).append("</span>\n");
                sb.append("      <span style=\"direction: ltr;\">").append(escapeHtml(header.getTime() != null ? header.getTime() : "")).append("</span>\n");
                sb.append("    </div>\n");
                sb.append("  </td>\n");
                sb.append("</tr>\n");
                sb.append("</table>\n");

            } else if (el instanceof ItemsTableElement items) {
                sb.append("<table class=\"items-table\">\n");
                sb.append("<thead>\n");
                sb.append("<tr>\n");
                sb.append("  <th class=\"col-item\">الصنف</th>\n");
                sb.append("  <th class=\"col-qty\">الكمية</th>\n");
                sb.append("  <th class=\"col-price\">السعر</th>\n");
                sb.append("  <th class=\"col-total\">الإجمالي</th>\n");
                sb.append("</tr>\n");
                sb.append("</thead>\n");
                sb.append("<tbody>\n");

                for (ItemsTableElement.ItemRow row : items.getItems()) {
                    sb.append("<tr>\n");
                    sb.append("  <td class=\"col-item\">").append(escapeHtml(row.getName())).append("</td>\n");
                    sb.append("  <td class=\"col-qty\">").append(escapeHtml(row.getQuantity())).append("</td>\n");
                    sb.append("  <td class=\"col-price\">").append(escapeHtml(row.getUnitPrice())).append("</td>\n");
                    sb.append("  <td class=\"col-total\">").append(escapeHtml(row.getTotalPrice())).append("</td>\n");
                    sb.append("</tr>\n");
                }

                sb.append("</tbody>\n");
                sb.append("</table>\n");

            } else if (el instanceof TotalsElement totals) {
                sb.append("<div class=\"totals-block\">\n");

                // Total pieces line: إجمالي قطع : [count] [amount]
                sb.append("<div class=\"total-pieces-line\">\n");
                sb.append("  <span class=\"label-part\">إجمالي قطع :</span>\n");
                sb.append("  <span class=\"qty-part\">").append(escapeHtml(totals.getTotalItemsCount())).append("</span>\n");
                sb.append("  <span class=\"amt-part\">").append(escapeHtml(totals.getTotalItemsAmount())).append("</span>\n");
                sb.append("</div>\n");

                sb.append("<hr class=\"double-divider\"/>\n");

                // Discount
                renderTotalsRow(sb, "خصم الفاتورة :", totals.getDiscountAmount());
                sb.append("<hr class=\"solid-divider\"/>\n");

                // Net invoice
                renderTotalsRow(sb, "صافى الفاتورة :", totals.getNetTotal());
                sb.append("<hr class=\"solid-divider\"/>\n");

                // Paid amount
                renderTotalsRow(sb, "المدفوع :", totals.getPaidAmount());
                sb.append("<hr class=\"solid-divider\"/>\n");

                // Remaining amount
                renderTotalsRow(sb, "الباقى :", totals.getRemainingAmount());
                sb.append("<hr class=\"double-divider\"/>\n");

                sb.append("</div>\n");

            } else if (el instanceof SeparatorElement) {
                sb.append("<hr class=\"dashed-divider\"/>\n");

            } else if (el instanceof FooterElement footer) {
                sb.append("<div class=\"receipt-footer\">").append(escapeHtml(footer.getText())).append("</div>\n");
            }
        }

        sb.append("</div>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        return sb.toString();
    }

    private void renderMetaRow(StringBuilder sb, String label, String value) {
        String safeVal = value != null ? value : "";
        sb.append("<tr>\n");
        sb.append("  <td class=\"meta-label\">").append(escapeHtml(label)).append("</td>\n");
        sb.append("  <td class=\"meta-colon\">:</td>\n");
        sb.append("  <td class=\"meta-val\">").append(escapeHtml(safeVal)).append("</td>\n");
        sb.append("</tr>\n");
    }

    private void renderTotalsRow(StringBuilder sb, String label, String value) {
        String safeVal = value != null ? value : "0.00";
        sb.append("<div class=\"totals-row\">\n");
        sb.append("  <span class=\"tot-label\">").append(escapeHtml(label)).append("</span>\n");
        sb.append("  <span class=\"tot-value\">").append(escapeHtml(safeVal)).append("</span>\n");
        sb.append("</div>\n");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String generateBarcodeSvg(String code) {
        if (code == null || code.isBlank()) return "";
        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"barcode-svg\" viewBox=\"0 0 160 36\" xmlns=\"http://www.w3.org/2000/svg\">\n");
        svg.append("  <rect width=\"160\" height=\"36\" fill=\"#ffffff\"/>\n");

        // Deterministic barcode pattern based on characters
        int x = 10;
        int seed = Math.abs(code.hashCode());
        for (int i = 0; i < code.length() && x < 150; i++) {
            char c = code.charAt(i);
            int pattern = (c * 17 + seed + i * 31) % 16;
            int barWidth1 = (pattern % 2 == 0) ? 2 : 3;
            int spaceWidth1 = (pattern % 3 == 0) ? 1 : 2;
            int barWidth2 = (pattern > 8) ? 3 : 2;
            int spaceWidth2 = (pattern > 4) ? 2 : 1;

            svg.append("  <rect x=\"").append(x).append("\" y=\"0\" width=\"").append(barWidth1).append("\" height=\"36\" fill=\"#000000\"/>\n");
            x += barWidth1 + spaceWidth1;
            svg.append("  <rect x=\"").append(x).append("\" y=\"0\" width=\"").append(barWidth2).append("\" height=\"36\" fill=\"#000000\"/>\n");
            x += barWidth2 + spaceWidth2;
        }
        svg.append("</svg>\n");
        return svg.toString();
    }
}
