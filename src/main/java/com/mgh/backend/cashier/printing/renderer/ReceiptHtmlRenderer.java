package com.mgh.backend.cashier.printing.renderer;

import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptElement;
import com.mgh.backend.cashier.printing.model.elements.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ReceiptHtmlRenderer {

    private final int paperWidthMm;

    public ReceiptHtmlRenderer(@Value("${app.printer.paper-width-mm:80}") int paperWidthMm) {
        if (paperWidthMm != 58 && paperWidthMm != 80) {
            throw new IllegalArgumentException("Printer paper width must be 58 or 80 mm");
        }
        this.paperWidthMm = paperWidthMm;
    }

    public String render(ReceiptDocument doc) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html dir=\"rtl\" lang=\"ar\">\n");
        sb.append("<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>معاينة الفاتورة - ").append(paperWidthMm).append("mm</title>\n");
        sb.append("<style>\n");
        sb.append("  @page {\n");
        sb.append("    size: ").append(paperWidthMm).append("mm auto;\n");
        sb.append("    margin: 0;\n");
        sb.append("  }\n");
        sb.append("  * {\n");
        sb.append("    box-sizing: border-box;\n");
        sb.append("    margin: 0;\n");
        sb.append("    padding: 0;\n");
        sb.append("  }\n");
        sb.append("  body {\n");
        sb.append("    background-color: #f1f5f9;\n");
        sb.append("    font-family: 'Segoe UI', 'Cairo', Tahoma, Arial, sans-serif;\n");
        sb.append("    display: flex;\n");
        sb.append("    flex-direction: column;\n");
        sb.append("    align-items: center;\n");
        sb.append("    padding: 20px 0;\n");
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
        sb.append("    width: ").append(paperWidthMm).append("mm;\n");
        sb.append("    max-width: ").append(paperWidthMm).append("mm;\n");
        sb.append("    min-width: ").append(paperWidthMm).append("mm;\n");
        sb.append("    background: #ffffff;\n");
        sb.append("    padding: 6px 8px;\n");
        sb.append("    box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);\n");
        sb.append("    font-size: 11.5px;\n");
        sb.append("    line-height: 1.3;\n");
        sb.append("    color: #000000;\n");
        sb.append("    overflow: hidden;\n");
        sb.append("  }\n");
        sb.append("  .store-title {\n");
        sb.append("    text-align: center;\n");
        sb.append("    font-size: 16px;\n");
        sb.append("    font-weight: 900;\n");
        sb.append("    margin-bottom: 10px;\n");
        sb.append("    padding-bottom: 2px;\n");
        sb.append("    line-height: 1.2;\n");
        sb.append("  }\n");
        sb.append("  .receipt-header-grid {\n");
        sb.append("    width: 100%;\n");
        sb.append("    display: flex;\n");
        sb.append("    flex-direction: column;\n");
        sb.append("    gap: 3px;\n");
        sb.append("    font-size: 10.5px;\n");
        sb.append("    line-height: 1.35;\n");
        sb.append("    margin: 2px 0 8px 0;\n");
        sb.append("    padding-bottom: 2px;\n");
        sb.append("  }\n");
        sb.append("  .header-row {\n");
        sb.append("    display: flex;\n");
        sb.append("    justify-content: space-between;\n");
        sb.append("    align-items: center;\n");
        sb.append("    gap: 6px;\n");
        sb.append("    width: 100%;\n");
        sb.append("    white-space: nowrap;\n");
        sb.append("  }\n");
        sb.append("  .header-cell {\n");
        sb.append("    white-space: nowrap;\n");
        sb.append("    overflow: hidden;\n");
        sb.append("    text-overflow: ellipsis;\n");
        sb.append("    min-width: 0;\n");
        sb.append("  }\n");
        sb.append("  .header-cell.col-right {\n");
        sb.append("    text-align: right;\n");
        sb.append("    flex: 0 1 auto;\n");
        sb.append("  }\n");
        sb.append("  .header-cell.col-left {\n");
        sb.append("    text-align: left;\n");
        sb.append("    flex: 0 1 auto;\n");
        sb.append("  }\n");
        sb.append("  .receipt-header-grid .meta-label {\n");
        sb.append("    font-weight: 700;\n");
        sb.append("  }\n");
        sb.append("  .receipt-header-grid .meta-val {\n");
        sb.append("    font-weight: 600;\n");
        sb.append("  }\n");
        sb.append("  .ltr-val {\n");
        sb.append("    direction: ltr;\n");
        sb.append("    unicode-bidi: isolate;\n");
        sb.append("    display: inline-block;\n");
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
        sb.append("    margin: 3px 0;\n");
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
        sb.append("    table-layout: fixed;\n");
        sb.append("    border-collapse: collapse;\n");
        sb.append("    margin: 6px 0 4px 0;\n");
        sb.append("  }\n");
        sb.append("  .items-table th {\n");
        sb.append("    font-weight: 800;\n");
        sb.append("    font-size: 11px;\n");
        sb.append("    padding: 3px 2px 5px 2px;\n");
        sb.append("    border-bottom: 1px solid #000000;\n");
        sb.append("  }\n");
        sb.append("  .items-table tbody tr:first-child td {\n");
        sb.append("    padding-top: 5px;\n");
        sb.append("  }\n");
        sb.append("  .items-table td {\n");
        sb.append("    padding: 2.5px 2px;\n");
        sb.append("    vertical-align: top;\n");
        sb.append("    border: none;\n");
        sb.append("  }\n");
        sb.append("  .col-item {\n");
        sb.append("    text-align: right;\n");
        sb.append("    width: 52%;\n");
        sb.append("    font-size: 11.5px;\n");
        sb.append("    font-weight: 700;\n");
        sb.append("    word-wrap: break-word;\n");
        sb.append("    overflow-wrap: anywhere;\n");
        sb.append("    line-height: 1.25;\n");
        sb.append("    padding-left: 4px;\n");
        sb.append("  }\n");
        sb.append("  .col-qty {\n");
        sb.append("    text-align: center;\n");
        sb.append("    width: 12%;\n");
        sb.append("    font-size: 11px;\n");
        sb.append("    font-weight: 600;\n");
        sb.append("    white-space: nowrap;\n");
        sb.append("    direction: ltr;\n");
        sb.append("  }\n");
        sb.append("  .col-price {\n");
        sb.append("    text-align: center;\n");
        sb.append("    width: 18%;\n");
        sb.append("    font-size: 11px;\n");
        sb.append("    font-weight: 700;\n");
        sb.append("    white-space: nowrap;\n");
        sb.append("    direction: ltr;\n");
        sb.append("  }\n");
        sb.append("  .col-total {\n");
        sb.append("    text-align: left;\n");
        sb.append("    width: 18%;\n");
        sb.append("    font-size: 11.5px;\n");
        sb.append("    font-weight: 800;\n");
        sb.append("    white-space: nowrap;\n");
        sb.append("    direction: ltr;\n");
        sb.append("  }\n");
        sb.append("  .totals-block {\n");
        sb.append("    width: 100%;\n");
        sb.append("  }\n");
        sb.append("  .total-pieces-line {\n");
        sb.append("    display: flex;\n");
        sb.append("    align-items: center;\n");
        sb.append("    font-weight: 800;\n");
        sb.append("    font-size: 12px;\n");
        sb.append("    padding: 2px 0;\n");
        sb.append("    width: 100%;\n");
        sb.append("  }\n");
        sb.append("  .total-pieces-line .label-part {\n");
        sb.append("    width: 52%;\n");
        sb.append("    text-align: right;\n");
        sb.append("    white-space: nowrap;\n");
        sb.append("  }\n");
        sb.append("  .total-pieces-line .qty-part {\n");
        sb.append("    width: 12%;\n");
        sb.append("    text-align: center;\n");
        sb.append("    white-space: nowrap;\n");
        sb.append("    direction: ltr;\n");
        sb.append("  }\n");
        sb.append("  .total-pieces-line .amt-part {\n");
        sb.append("    width: 36%;\n");
        sb.append("    text-align: left;\n");
        sb.append("    white-space: nowrap;\n");
        sb.append("    direction: ltr;\n");
        sb.append("  }\n");
        sb.append("  .totals-row {\n");
        sb.append("    display: flex;\n");
        sb.append("    justify-content: space-between;\n");
        sb.append("    align-items: center;\n");
        sb.append("    padding: 2px 0;\n");
        sb.append("    font-size: 11.5px;\n");
        sb.append("  }\n");
        sb.append("  .totals-row .tot-label {\n");
        sb.append("    font-weight: 700;\n");
        sb.append("  }\n");
        sb.append("  .totals-row .tot-value {\n");
        sb.append("    text-align: left;\n");
        sb.append("    direction: ltr;\n");
        sb.append("    font-weight: 700;\n");
        sb.append("  }\n");
        sb.append("  .final-total-box {\n");
        sb.append("    margin: 3px 0;\n");
        sb.append("    padding: 4px 6px;\n");
        sb.append("    border: 2px solid #000000;\n");
        sb.append("    background-color: #ffffff;\n");
        sb.append("    display: flex;\n");
        sb.append("    justify-content: space-between;\n");
        sb.append("    align-items: center;\n");
        sb.append("  }\n");
        sb.append("  .final-total-box .final-label {\n");
        sb.append("    font-size: 13.5px;\n");
        sb.append("    font-weight: 900;\n");
        sb.append("  }\n");
        sb.append("  .final-total-box .final-value {\n");
        sb.append("    font-size: 15.5px;\n");
        sb.append("    font-weight: 900;\n");
        sb.append("    direction: ltr;\n");
        sb.append("    text-align: left;\n");
        sb.append("  }\n");
        sb.append("  .receipt-footer {\n");
        sb.append("    text-align: center;\n");
        sb.append("    margin-top: 5px;\n");
        sb.append("    font-size: 11px;\n");
        sb.append("    font-weight: 600;\n");
        sb.append("  }\n");
        sb.append("  .barcode-container {\n");
        sb.append("    text-align: center;\n");
        sb.append("    margin: 6px 0 2px 0;\n");
        sb.append("    display: flex;\n");
        sb.append("    flex-direction: column;\n");
        sb.append("    align-items: center;\n");
        sb.append("    justify-content: center;\n");
        sb.append("  }\n");
        sb.append("  .barcode-svg {\n");
        sb.append("    height: 24px;\n");
        sb.append("    max-width: 65%;\n");
        sb.append("    width: auto;\n");
        sb.append("  }\n");
        sb.append("  .barcode-val {\n");
        sb.append("    font-size: 10px;\n");
        sb.append("    font-weight: 600;\n");
        sb.append("    letter-spacing: 1px;\n");
        sb.append("    margin-top: 2px;\n");
        sb.append("    direction: ltr;\n");
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
        sb.append("      padding: 1.5mm 2mm;\n");
        sb.append("      width: ").append(paperWidthMm).append("mm;\n");
        sb.append("      max-width: ").append(paperWidthMm).append("mm;\n");
        sb.append("    }\n");
        sb.append("    .final-total-box {\n");
        sb.append("      background-color: transparent !important;\n");
        sb.append("      border: 2px solid #000000 !important;\n");
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

        String receiptNumber = "";
        for (ReceiptElement el : doc.getElements()) {
            if (el instanceof HeaderElement h && h.getReceiptNumber() != null && !h.getReceiptNumber().isBlank()) {
                receiptNumber = h.getReceiptNumber();
                break;
            }
        }

        for (ReceiptElement el : doc.getElements()) {
            if (el instanceof HeaderElement header) {
                if (header.getStoreName() != null && !header.getStoreName().isBlank()) {
                    sb.append("<div class=\"store-title\">").append(escapeHtml(header.getStoreName())).append("</div>\n");
                }

                String branchStr = escapeHtml(header.getBranch() != null ? header.getBranch() : "");
                String phoneStr = escapeHtml(header.getPhone() != null ? header.getPhone() : "");
                String addressStr = escapeHtml(header.getAddress() != null ? header.getAddress() : "");
                String cashierStr = escapeHtml(header.getCashierName() != null ? header.getCashierName() : "");
                String receiptNumStr = escapeHtml(header.getReceiptNumber() != null ? header.getReceiptNumber() : "");
                String dateTimeStr = escapeHtml(
                        (header.getDate() != null ? header.getDate() : "") +
                        (header.getTime() != null && !header.getTime().isBlank() ? " " + header.getTime() : "")
                );

                sb.append("<div class=\"receipt-header-grid\">\n");
                // Row 1: Left: Branch name | Right: Branch phone number
                sb.append("  <div class=\"header-row\">\n");
                sb.append("    <div class=\"header-cell col-right\"><span class=\"meta-label\">الهاتف:</span> <span class=\"meta-val ltr-val\">").append(phoneStr).append("</span></div>\n");
                sb.append("    <div class=\"header-cell col-left\"><span class=\"meta-label\">الفرع:</span> <span class=\"meta-val\">").append(branchStr).append("</span></div>\n");
                sb.append("  </div>\n");

                // Row 2: Left: Branch address | Right: Cashier name
                sb.append("  <div class=\"header-row\">\n");
                sb.append("    <div class=\"header-cell col-right\"><span class=\"meta-label\">الكاشير:</span> <span class=\"meta-val\">").append(cashierStr).append("</span></div>\n");
                sb.append("    <div class=\"header-cell col-left\"><span class=\"meta-label\">العنوان:</span> <span class=\"meta-val\">").append(addressStr).append("</span></div>\n");
                sb.append("  </div>\n");

                // Row 3: Left: Receipt number | Right: Receipt date and time
                sb.append("  <div class=\"header-row\">\n");
                sb.append("    <div class=\"header-cell col-right\"><span class=\"meta-label\">التاريخ:</span> <span class=\"meta-val ltr-val\">").append(dateTimeStr).append("</span></div>\n");
                sb.append("    <div class=\"header-cell col-left\"><span class=\"meta-label\">رقم الفاتورة:</span> <span class=\"meta-val ltr-val\">").append(receiptNumStr).append("</span></div>\n");
                sb.append("  </div>\n");
                sb.append("</div>\n");

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

                // Net invoice (Final Total - Strong Visual Emphasis)
                sb.append("<div class=\"final-total-box\">\n");
                sb.append("  <span class=\"final-label\">صافى الفاتورة :</span>\n");
                sb.append("  <span class=\"final-value\">").append(escapeHtml(totals.getNetTotal())).append("</span>\n");
                sb.append("</div>\n");

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

        // Barcode at the end of the receipt
        if (!receiptNumber.isBlank()) {
            sb.append("<div class=\"barcode-container\">\n");
            sb.append(generateBarcodeSvg(receiptNumber));
            sb.append("<div class=\"barcode-val\">").append(escapeHtml(receiptNumber)).append("</div>\n");
            sb.append("</div>\n");
        }

        sb.append("</div>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        return sb.toString();
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
        int quietZone = 8;
        int x = quietZone;
        int seed = Math.abs(code.hashCode());
        List<int[]> bars = new ArrayList<>();

        // Start guard bars
        bars.add(new int[]{x, 2});
        x += 3;
        bars.add(new int[]{x, 1});
        x += 3;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            int pattern = (c * 17 + seed + i * 31) % 16;
            int barWidth1 = (pattern % 2 == 0) ? 1 : 2;
            int spaceWidth1 = (pattern % 3 == 0) ? 1 : 2;
            int barWidth2 = (pattern > 8) ? 2 : 1;
            int spaceWidth2 = (pattern > 4) ? 2 : 1;

            bars.add(new int[]{x, barWidth1});
            x += barWidth1 + spaceWidth1;
            bars.add(new int[]{x, barWidth2});
            x += barWidth2 + spaceWidth2;
        }

        // Stop guard bars
        bars.add(new int[]{x, 2});
        x += 3;
        bars.add(new int[]{x, 1});
        x += 1;

        int totalWidth = x + quietZone;
        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"barcode-svg\" viewBox=\"0 0 ").append(totalWidth).append(" 24\" xmlns=\"http://www.w3.org/2000/svg\">\n");
        svg.append("  <rect width=\"").append(totalWidth).append("\" height=\"24\" fill=\"#ffffff\"/>\n");
        for (int[] bar : bars) {
            svg.append("  <rect x=\"").append(bar[0]).append("\" y=\"0\" width=\"").append(bar[1]).append("\" height=\"24\" fill=\"#000000\"/>\n");
        }
        svg.append("</svg>\n");
        return svg.toString();
    }
}
