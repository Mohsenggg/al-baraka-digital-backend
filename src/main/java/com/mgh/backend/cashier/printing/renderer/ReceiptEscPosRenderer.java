package com.mgh.backend.cashier.printing.renderer;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.barcode.BarCode;
import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptElement;
import com.mgh.backend.cashier.printing.model.elements.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReceiptEscPosRenderer {

    private static final int MARGIN_DOTS = 8;
    private static final int RASTER_COMMAND_HEIGHT_LIMIT = 65_535;
    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(new AffineTransform(), true,
            true);

    private final int paperWidthDots;

    public ReceiptEscPosRenderer(@Value("${app.printer.paper-width-mm:80}") int paperWidthMm) {
        this.paperWidthDots = switch (paperWidthMm) {
            case 58 -> 384;
            case 80 -> 576;
            default -> throw new IllegalArgumentException("Printer paper width must be 58 or 80 mm");
        };
    }

    public byte[] render(ReceiptDocument doc) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(new byte[] { 0x1B, 0x40 });

        EscPos escpos = new EscPos(output);
        RasterBlock currentBlock = buildBlock(doc);

        String receiptNumber = extractReceiptNumber(doc);

        writeRaster(output, currentBlock);

        // Barcode at the end of the receipt
        if (receiptNumber != null && !receiptNumber.isBlank()) {
            escpos.feed(1);
            BarCode barcode = new BarCode();
            barcode.setSystem(BarCode.BarCodeSystem.CODE128);
            barcode.setBarCodeSize(2, 45);
            barcode.setHRIPosition(BarCode.BarCodeHRIPosition.BelowBarCode);
            barcode.setHRIFont(BarCode.BarCodeHRIFont.Font_B);
            barcode.setJustification(EscPosConst.Justification.Center);
            escpos.write(barcode, "{B" + receiptNumber);
            escpos.feed(1);
        }

        escpos.feed(3);
        escpos.cut(EscPos.CutMode.FULL);
        escpos.close();
        return output.toByteArray();
    }

    private void addHeaderRow(
            RasterBlock block,
            String leftLabel, String leftValue,
            String rightLabel, String rightValue) {
        String safeLeftLabel = valueOrEmpty(leftLabel).trim();
        String safeLeftVal = valueOrEmpty(leftValue).trim();
        String safeRightLabel = valueOrEmpty(rightLabel).trim();
        String safeRightVal = valueOrEmpty(rightValue).trim();

        int contentWidth = contentWidth();
        int innerGap = scale(4);
        int colGap = scale(14);
        int available = contentWidth - colGap;

        int fontSize = scale(16);
        int minFontSize = scale(11);
        Font labelFont = font(fontSize, true);
        Font valFont = font(fontSize, false);

        while (fontSize > minFontSize) {
            float leftTotal = fieldWidth(safeLeftLabel, safeLeftVal, labelFont, valFont, innerGap);
            float rightTotal = fieldWidth(safeRightLabel, safeRightVal, labelFont, valFont, innerGap);
            if (leftTotal + rightTotal <= available) {
                break;
            }
            fontSize--;
            labelFont = font(fontSize, true);
            valFont = font(fontSize, false);
        }

        float leftLabelW = textWidth(safeLeftLabel, labelFont);
        float rightLabelW = textWidth(safeRightLabel, labelFont);
        float leftValW = textWidth(safeLeftVal, valFont);
        float rightValW = textWidth(safeRightVal, valFont);

        float leftTotal = leftLabelW + (safeLeftVal.isEmpty() ? 0 : innerGap + leftValW);
        float rightTotal = rightLabelW + (safeRightVal.isEmpty() ? 0 : innerGap + rightValW);

        if (leftTotal + rightTotal > available) {
            int half = available / 2;
            int leftBudget;
            int rightBudget;
            if (leftTotal <= half) {
                leftBudget = (int) Math.ceil(leftTotal);
                rightBudget = available - leftBudget;
            } else if (rightTotal <= half) {
                rightBudget = (int) Math.ceil(rightTotal);
                leftBudget = available - rightBudget;
            } else {
                leftBudget = half;
                rightBudget = available - half;
            }

            int maxLeftValW = Math.max(scale(20), leftBudget - (int) Math.ceil(leftLabelW) - innerGap);
            int maxRightValW = Math.max(scale(20), rightBudget - (int) Math.ceil(rightLabelW) - innerGap);
            safeLeftVal = ellipsize(safeLeftVal, valFont, maxLeftValW);
            safeRightVal = ellipsize(safeRightVal, valFont, maxRightValW);
            leftValW = textWidth(safeLeftVal, valFont);
            rightValW = textWidth(safeRightVal, valFont);
        }

        List<TextCell> cells = new ArrayList<>(4);

        // Left field: flush to MARGIN_DOTS; in RTL, value is on the left, label is to its right
        int leftValWidth = safeLeftVal.isEmpty() ? 0 : (int) Math.ceil(leftValW) + 2;
        int leftLabelWidth = (int) Math.ceil(leftLabelW) + 2;
        if (!safeLeftVal.isEmpty()) {
            cells.add(cell(safeLeftVal, MARGIN_DOTS, leftValWidth, Alignment.LEFT, valFont));
            cells.add(cell(safeLeftLabel, MARGIN_DOTS + leftValWidth + innerGap, leftLabelWidth, Alignment.LEFT, labelFont));
        } else {
            cells.add(cell(safeLeftLabel, MARGIN_DOTS, leftLabelWidth, Alignment.LEFT, labelFont));
        }

        // Right field: flush to right margin (MARGIN_DOTS + contentWidth); label on far right, value to its left
        int rightEdge = MARGIN_DOTS + contentWidth;
        int rightLabelWidth = (int) Math.ceil(rightLabelW) + 2;
        int rightLabelX = rightEdge - rightLabelWidth;
        cells.add(cell(safeRightLabel, rightLabelX, rightLabelWidth, Alignment.RIGHT, labelFont));

        if (!safeRightVal.isEmpty()) {
            int rightValWidth = (int) Math.ceil(rightValW) + 2;
            int rightValX = rightLabelX - innerGap - rightValWidth;
            cells.add(cell(safeRightVal, rightValX, rightValWidth, Alignment.RIGHT, valFont));
        }

        block.addLine(cells, scale(24));
    }

    private float fieldWidth(String label, String value, Font labelFont, Font valFont, int innerGap) {
        float lw = textWidth(label, labelFont);
        if (value == null || value.isEmpty()) {
            return lw + 2;
        }
        return lw + innerGap + textWidth(value, valFont) + 4;
    }

    private void addField(RasterBlock block, String label, String value) {
        int contentWidth = contentWidth();
        int valWidth = contentWidth * 55 / 100;
        int labelWidth = contentWidth - valWidth;
        String safeVal = valueOrEmpty(value);
        Font valFont = fitFont(safeVal, font(scale(18), true), valWidth - 4, scale(12));
        Font labelFont = fitFont(label, font(scale(18), true), labelWidth - 4, scale(12));
        block.addLine(List.of(
                cell(safeVal, MARGIN_DOTS, valWidth - 4, Alignment.LEFT, valFont),
                cell(label, MARGIN_DOTS + valWidth, labelWidth, Alignment.RIGHT, labelFont)
        ), scale(26));
    }

    private void addFinalTotalLine(RasterBlock block, String label, String amount) {
        int contentWidth = contentWidth();
        int pad = scale(8);
        int innerWidth = contentWidth - 2 * pad;
        int valWidth = innerWidth * 55 / 100;
        int labelWidth = innerWidth - valWidth;
        String safeAmount = valueOrEmpty(amount);

        Font amountFont = fitFont(safeAmount, font(scale(24), true), valWidth - 4, scale(14));
        Font labelFont = fitFont(label, font(scale(20), true), labelWidth - 4, scale(14));

        block.addBlank(scale(2));
        block.addBoxedLine(List.of(
                cell(safeAmount, MARGIN_DOTS + pad, valWidth - 4, Alignment.LEFT, amountFont),
                cell(label, MARGIN_DOTS + pad + valWidth, labelWidth, Alignment.RIGHT, labelFont)
        ), scale(34));
        block.addBlank(scale(2));
    }

    private void addTotalPiecesLine(RasterBlock block, String amount, String quantity, String label) {
        int[] widths = itemColumnWidths();
        int amtColWidth = widths[0] + widths[1];
        int qtyColWidth = widths[2];
        int labelColWidth = widths[3];

        String safeAmt = valueOrEmpty(amount);
        String safeQty = valueOrEmpty(quantity);
        Font baseFont = font(scale(19), true);

        Font amtFont = fitFont(safeAmt, baseFont, amtColWidth - 4, scale(12));
        Font qtyFont = fitFont(safeQty, baseFont, qtyColWidth - 4, scale(12));
        Font labelFont = fitFont(label, baseFont, labelColWidth - 4, scale(12));

        block.addLine(List.of(
                cell(safeAmt, MARGIN_DOTS, amtColWidth - 4, Alignment.LEFT, amtFont),
                cell(safeQty, MARGIN_DOTS + amtColWidth + 2, qtyColWidth - 4, Alignment.CENTER, qtyFont),
                cell(label, MARGIN_DOTS + amtColWidth + qtyColWidth + 4, labelColWidth - 4, Alignment.RIGHT, labelFont)
        ), scale(28));
    }

    private void addItemsHeader(RasterBlock block) {
        int[] widths = itemColumnWidths();
        int x0 = MARGIN_DOTS;
        int x1 = x0 + widths[0];
        int x2 = x1 + widths[1];
        int x3 = x2 + widths[2];
        Font headerFont = font(scale(17), true);

        block.addBlank(scale(8));
        block.addLine(List.of(
                cell("الإجمالي", x0, widths[0] - 4, Alignment.LEFT, headerFont),
                cell("السعر", x1 + 2, widths[1] - 4, Alignment.CENTER, headerFont),
                cell("الكمية", x2 + 2, widths[2] - 4, Alignment.CENTER, headerFont),
                cell("الصنف", x3 + 4, widths[3] - 4, Alignment.RIGHT, headerFont)
        ), scale(26));
        block.addRule(Rule.SOLID);
        block.addBlank(scale(4));
    }

    private void addItemRow(RasterBlock block, ItemsTableElement.ItemRow row) {
        int[] widths = itemColumnWidths();
        int x0 = MARGIN_DOTS;
        int x1 = x0 + widths[0];
        int x2 = x1 + widths[1];
        int x3 = x2 + widths[2];

        String totalStr = valueOrEmpty(row.getTotalPrice());
        String priceStr = valueOrEmpty(row.getUnitPrice());
        String qtyStr = valueOrEmpty(row.getQuantity());
        String nameStr = valueOrEmpty(row.getName());

        Font nameFont = font(scale(18), true);
        Font totalFont = fitFont(totalStr, font(scale(18), true), widths[0] - 4, scale(11));
        Font priceFont = fitFont(priceStr, font(scale(17), true), widths[1] - 4, scale(11));
        Font qtyFont = fitFont(qtyStr, font(scale(17), false), widths[2] - 4, scale(11));

        int nameColWidth = widths[3] - 6;
        List<String> nameLines = wrapText(nameStr, nameFont, nameColWidth);
        if (nameLines.isEmpty()) {
            nameLines = List.of("");
        }

        block.addLine(List.of(
                cell(totalStr, x0, widths[0] - 4, Alignment.LEFT, totalFont),
                cell(priceStr, x1 + 2, widths[1] - 4, Alignment.CENTER, priceFont),
                cell(qtyStr, x2 + 2, widths[2] - 4, Alignment.CENTER, qtyFont),
                cell(nameLines.get(0), x3 + 6, nameColWidth, Alignment.RIGHT, nameFont)
        ), scale(26));

        for (int i = 1; i < nameLines.size(); i++) {
            block.addLine(List.of(
                    cell(nameLines.get(i), x3 + 6, nameColWidth, Alignment.RIGHT, nameFont)
            ), scale(24));
        }
    }

    private Font fitFont(String text, Font baseFont, int maxWidth, int minSize) {
        if (text == null || text.isEmpty() || textWidth(text, baseFont) <= maxWidth) {
            return baseFont;
        }
        int size = baseFont.getSize();
        int style = baseFont.getStyle();
        Font current = baseFont;
        while (size > minSize && textWidth(text, current) > maxWidth) {
            size--;
            current = new Font(Font.SANS_SERIF, style, size);
        }
        return current;
    }

    private String ellipsize(String text, Font font, int maxWidth) {
        if (text == null || text.isEmpty() || textWidth(text, font) <= maxWidth) {
            return text != null ? text : "";
        }
        String ellipsis = "...";
        int len = text.length();
        while (len > 1 && textWidth(text.substring(0, len).trim() + ellipsis, font) > maxWidth) {
            len--;
        }
        return text.substring(0, Math.max(1, len)).trim() + ellipsis;
    }

    private List<String> wrapText(String text, Font font, int maxWidth) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (textWidth(word, font) > maxWidth) {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                for (int i = 0; i < word.length(); i++) {
                    String ch = String.valueOf(word.charAt(i));
                    String candidate = current.toString() + ch;
                    if (!current.isEmpty() && textWidth(candidate, font) > maxWidth) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    current.append(ch);
                }
                continue;
            }

            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && textWidth(candidate, font) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void writeRaster(ByteArrayOutputStream output, RasterBlock block) throws IOException {
        if (block.isEmpty()) {
            return;
        }

        int height = block.height();
        if (height > RASTER_COMMAND_HEIGHT_LIMIT) {
            throw new IllegalArgumentException("Receipt is too long to render as one printer image");
        }

        BufferedImage image = renderToImage(block);
        writeRasterImage(output, image);
    }

    BufferedImage renderToImage(ReceiptDocument doc) {
        return renderToImage(buildBlock(doc));
    }

    private String extractReceiptNumber(ReceiptDocument doc) {
        for (ReceiptElement element : doc.getElements()) {
            if (element instanceof HeaderElement header) {
                return header.getReceiptNumber();
            }
        }
        return null;
    }

    private RasterBlock buildBlock(ReceiptDocument doc) {
        RasterBlock block = new RasterBlock();
        for (ReceiptElement element : doc.getElements()) {
            if (element instanceof HeaderElement header) {
                if (header.getStoreName() != null && !header.getStoreName().isBlank()) {
                    Font storeFont = fitFont(header.getStoreName(), font(scale(28), true), contentWidth(), scale(16));
                    block.addText(header.getStoreName(), storeFont, Alignment.CENTER, scale(34));
                }
                block.addBlank(scale(16));
                addHeaderRow(block, "الفرع:", valueOrEmpty(header.getBranch()), "الهاتف:", valueOrEmpty(header.getPhone()));
                addHeaderRow(block, "العنوان:", valueOrEmpty(header.getAddress()), "الكاشير:", valueOrEmpty(header.getCashierName()));
                String dateTimeStr = (valueOrEmpty(header.getDate()) +
                        (header.getTime() != null && !header.getTime().isBlank() ? " " + header.getTime() : "")).trim();
                addHeaderRow(block, "رقم الفاتورة:", valueOrEmpty(header.getReceiptNumber()), "التاريخ:", dateTimeStr);
                block.addBlank(scale(10));
            } else if (element instanceof ItemsTableElement items) {
                addItemsHeader(block);
                if (items.getItems() != null) {
                    for (ItemsTableElement.ItemRow row : items.getItems()) {
                        addItemRow(block, row);
                    }
                }
            } else if (element instanceof TotalsElement totals) {
                addTotalPiecesLine(block, totals.getTotalItemsAmount(), totals.getTotalItemsCount(), "إجمالي قطع :");
                block.addRule(Rule.DOUBLE);
                addField(block, "خصم الفاتورة :", totals.getDiscountAmount());
                addFinalTotalLine(block, "صافى الفاتورة :", totals.getNetTotal());
                addField(block, "المدفوع :", totals.getPaidAmount());
                block.addRule(Rule.SOLID);
                addField(block, "الباقى :", totals.getRemainingAmount());
                block.addRule(Rule.DOUBLE);
            } else if (element instanceof SeparatorElement) {
                block.addRule(Rule.DASHED);
            } else if (element instanceof FooterElement footer) {
                block.addBlank(scale(4));
                Font footerFont = font(scale(17), true);
                for (String line : wrapText(valueOrEmpty(footer.getText()), footerFont, contentWidth())) {
                    block.addText(line, footerFont, Alignment.CENTER, scale(24));
                }
            }
        }
        return block;
    }

    private BufferedImage renderToImage(RasterBlock block) {
        int height = Math.max(1, block.height());
        BufferedImage image = new BufferedImage(paperWidthDots, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, paperWidthDots, height);
            graphics.setColor(Color.BLACK);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int y = 0;
            for (BlockLine line : block.lines()) {
                if (line.rule() == Rule.DASHED || line.rule() == Rule.SOLID || line.rule() == Rule.DOUBLE) {
                    graphics.setStroke(line.rule() == Rule.DASHED
                            ? new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 4, 3 },
                                    0)
                            : new BasicStroke(1));
                    int midY = y + line.height() / 2;
                    int rightX = paperWidthDots - MARGIN_DOTS - 1;
                    graphics.drawLine(MARGIN_DOTS, midY, rightX, midY);
                    if (line.rule() == Rule.DOUBLE) {
                        graphics.drawLine(MARGIN_DOTS, midY + 3, rightX, midY + 3);
                    }
                } else {
                    if (line.rule() == Rule.BOX) {
                        graphics.setStroke(new BasicStroke(2));
                        graphics.drawRect(MARGIN_DOTS + 1, y + 1, contentWidth() - 3, line.height() - 2);
                    }
                    for (TextCell textCell : line.cells()) {
                        drawCell(graphics, textCell, y, line.height());
                    }
                }
                y += line.height();
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void writeRasterImage(ByteArrayOutputStream output, BufferedImage image) throws IOException {
        int bytesPerRow = (paperWidthDots + 7) / 8;
        int height = image.getHeight();
        output.write(new byte[] {
                0x1D, 0x76, 0x30, 0x00,
                (byte) (bytesPerRow & 0xFF),
                (byte) ((bytesPerRow >> 8) & 0xFF),
                (byte) (height & 0xFF),
                (byte) ((height >> 8) & 0xFF)
        });

        for (int y = 0; y < height; y++) {
            for (int byteX = 0; byteX < bytesPerRow; byteX++) {
                int bits = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = byteX * 8 + bit;
                    if (x < paperWidthDots && isBlack(image.getRGB(x, y))) {
                        bits |= 0x80 >> bit;
                    }
                }
                output.write(bits);
            }
        }
    }

    private void drawCell(Graphics2D graphics, TextCell cell, int top, int lineHeight) {
        if (cell.text().isEmpty() || cell.width() <= 0) {
            return;
        }
        Shape previousClip = graphics.getClip();
        graphics.clipRect(cell.x(), top, cell.width(), lineHeight);
        try {
            TextLayout layout = new TextLayout(cell.text(), cell.font(), graphics.getFontRenderContext());
            float advance = layout.getAdvance();
            float x = switch (cell.alignment()) {
                case LEFT -> cell.x();
                case CENTER -> cell.x() + Math.max(0f, (cell.width() - advance) / 2f);
                case RIGHT -> cell.x() + Math.max(0f, cell.width() - advance);
            };
            float baseline = top + (lineHeight - layout.getAscent() - layout.getDescent()) / 2f + layout.getAscent();
            layout.draw(graphics, x, baseline);
        } finally {
            graphics.setClip(previousClip);
        }
    }

    private boolean isBlack(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return red * 299 + green * 587 + blue * 114 < 150_000;
    }

    private float textWidth(String text, Font font) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        TextLayout layout = new TextLayout(text, font, FONT_RENDER_CONTEXT);
        return Math.max(layout.getAdvance(), (float) layout.getBounds().getWidth());
    }

    private int scale(int size80mm) {
        if (paperWidthDots == 576) {
            return size80mm;
        }
        return Math.max(9, Math.round(size80mm * (float) paperWidthDots / 576f));
    }

    private TextCell cell(String text, int x, int width, Alignment alignment, Font font) {
        return new TextCell(text, x, width, alignment, font);
    }

    private Font font(int size, boolean bold) {
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size);
    }

    private int[] itemColumnWidths() {
        int contentWidth = contentWidth();
        int total = contentWidth * 18 / 100;
        int price = contentWidth * 18 / 100;
        int quantity = contentWidth * 12 / 100;
        return new int[] { total, price, quantity, contentWidth - total - price - quantity };
    }

    private int contentWidth() {
        return paperWidthDots - 2 * MARGIN_DOTS;
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private enum Rule {
        NONE,
        SOLID,
        DASHED,
        DOUBLE,
        BOX
    }

    private record TextCell(String text, int x, int width, Alignment alignment, Font font) {
    }

    private record BlockLine(List<TextCell> cells, int height, Rule rule) {
    }

    private final class RasterBlock {
        private final List<BlockLine> lines = new ArrayList<>();
        private int height;

        private void addText(String text, Font font, Alignment alignment, int lineHeight) {
            addLine(List.of(new TextCell(text, MARGIN_DOTS, contentWidth(), alignment, font)), lineHeight);
        }

        private void addLine(List<TextCell> cells, int lineHeight) {
            lines.add(new BlockLine(cells, lineHeight, Rule.NONE));
            height += lineHeight;
        }

        private void addBoxedLine(List<TextCell> cells, int lineHeight) {
            lines.add(new BlockLine(cells, lineHeight, Rule.BOX));
            height += lineHeight;
        }

        private void addBlank(int lineHeight) {
            addLine(List.of(), lineHeight);
        }

        private void addRule(Rule rule) {
            int lineHeight = switch (rule) {
                case DOUBLE -> scale(10);
                case DASHED -> scale(12);
                case SOLID -> scale(6);
                default -> scale(8);
            };
            lines.add(new BlockLine(List.of(), lineHeight, rule));
            height += lineHeight;
        }

        private List<BlockLine> lines() {
            return lines;
        }

        private int height() {
            return height;
        }

        private boolean isEmpty() {
            return lines.isEmpty();
        }
    }
}
