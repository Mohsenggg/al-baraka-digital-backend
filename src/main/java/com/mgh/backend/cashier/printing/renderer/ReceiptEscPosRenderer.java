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
    private static final FontRenderContext FONT_RENDER_CONTEXT =
            new FontRenderContext(new AffineTransform(), true, true);

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
        output.write(new byte[]{0x1B, 0x40});

        EscPos escpos = new EscPos(output);
        RasterBlock currentBlock = new RasterBlock();

        for (ReceiptElement element : doc.getElements()) {
            if (element instanceof HeaderElement header) {
                if (header.getStoreName() != null && !header.getStoreName().isBlank()) {
                    currentBlock.addText(header.getStoreName(), font(32, true), Alignment.CENTER, 40);
                }
                currentBlock.addBlank(8);
                addField(currentBlock, "الفرع        : ", header.getBranch());
                addField(currentBlock, "التليفون     : ", header.getPhone());
                addField(currentBlock, "العنوان      : ", header.getAddress());
                addField(currentBlock, "المستخدم     : ", header.getUser());

                if (header.getReceiptNumber() != null && !header.getReceiptNumber().isBlank()) {
                    currentBlock.addBlank(4);
                    writeRaster(output, currentBlock);
                    currentBlock = new RasterBlock();

                    BarCode barcode = new BarCode();
                    barcode.setSystem(BarCode.BarCodeSystem.CODE128);
                    barcode.setJustification(EscPosConst.Justification.Center);
                    escpos.write(barcode, "{B" + header.getReceiptNumber());
                    escpos.feed(1);
                    currentBlock.addBlank(8);
                }

                addField(currentBlock, "البائع       : ", header.getSeller());
                addField(currentBlock, "بون رقم      : ", header.getReceiptNumber());
                currentBlock.addLine(List.of(
                        cell(valueOrEmpty(header.getTime()), MARGIN_DOTS, contentWidth() / 3, Alignment.LEFT, font(20, false)),
                        cell(valueOrEmpty(header.getDate()), MARGIN_DOTS + contentWidth() / 3, contentWidth() / 3, Alignment.CENTER, font(20, false)),
                        cell("التاريخ      : ", MARGIN_DOTS + 2 * contentWidth() / 3, contentWidth() / 3, Alignment.RIGHT, font(20, false))
                ), 30);
            } else if (element instanceof ItemsTableElement items) {
                addItemsHeader(currentBlock);

                if (items.getItems() != null) {
                    for (ItemsTableElement.ItemRow row : items.getItems()) {
                        addItemRow(currentBlock, row);
                    }
                }
            } else if (element instanceof TotalsElement totals) {
                currentBlock.addTotalLine(totals.getTotalItemsAmount(), totals.getTotalItemsCount(), "إجمالي قطع : ");
                currentBlock.addRule(true);
                addField(currentBlock, "خصم الفاتورة : ", totals.getDiscountAmount());
                currentBlock.addRule(false);
                addField(currentBlock, "صافي الفاتورة: ", totals.getNetTotal());
                currentBlock.addRule(false);
                addField(currentBlock, "المدفوع      : ", totals.getPaidAmount());
                currentBlock.addRule(false);
                addField(currentBlock, "الباقي       : ", totals.getRemainingAmount());
                currentBlock.addRule(true);
            } else if (element instanceof SeparatorElement) {
                currentBlock.addRule(false);
            } else if (element instanceof FooterElement footer) {
                currentBlock.addBlank(8);
                currentBlock.addText(valueOrEmpty(footer.getText()), font(20, false), Alignment.CENTER, 30);
            }
        }

        writeRaster(output, currentBlock);
        escpos.feed(4);
        escpos.cut(EscPos.CutMode.FULL);
        escpos.close();
        return output.toByteArray();
    }

    private void addField(RasterBlock block, String label, String value) {
        int contentWidth = contentWidth();
        block.addLine(List.of(
                cell(valueOrEmpty(value), MARGIN_DOTS, contentWidth * 3 / 5, Alignment.LEFT, font(20, false)),
                cell(label, MARGIN_DOTS + contentWidth * 3 / 5, contentWidth * 2 / 5, Alignment.RIGHT, font(20, false))
        ), 30);
    }

    private void addItemsHeader(RasterBlock block) {
        int[] widths = itemColumnWidths();
        int x = MARGIN_DOTS;
        block.addLine(List.of(
                cell("الإجمالي", x, widths[0], Alignment.CENTER, font(18, true)),
                cell("السعر", x += widths[0], widths[1], Alignment.CENTER, font(18, true)),
                cell("الكمية", x += widths[1], widths[2], Alignment.CENTER, font(18, true)),
                cell("الصنف", x + widths[2], widths[3], Alignment.RIGHT, font(18, true))
        ), 28);
    }

    private void addItemRow(RasterBlock block, ItemsTableElement.ItemRow row) {
        int[] widths = itemColumnWidths();
        int x = MARGIN_DOTS;
        Font itemFont = font(18, false);
        List<String> nameLines = wrapText(valueOrEmpty(row.getName()), itemFont, widths[3]);
        if (nameLines.isEmpty()) {
            nameLines = List.of("");
        }

        block.addLine(List.of(
                cell(valueOrEmpty(row.getTotalPrice()), x, widths[0], Alignment.CENTER, itemFont),
                cell(valueOrEmpty(row.getUnitPrice()), x += widths[0], widths[1], Alignment.CENTER, itemFont),
                cell(valueOrEmpty(row.getQuantity()), x += widths[1], widths[2], Alignment.CENTER, itemFont),
                cell(nameLines.get(0), x + widths[2], widths[3], Alignment.RIGHT, itemFont)
        ), 27);

        for (int i = 1; i < nameLines.size(); i++) {
            block.addLine(List.of(
                    cell(nameLines.get(i), MARGIN_DOTS + widths[0] + widths[1] + widths[2], widths[3],
                            Alignment.RIGHT, itemFont)
            ), 25);
        }
    }

    private List<String> wrapText(String text, Font font, int width) {
        if (text.isBlank()) {
            return List.of("");
        }

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && textWidth(candidate, font) > width) {
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
                if (line.rule() != Rule.NONE) {
                    graphics.setStroke(line.rule() == Rule.DASHED
                            ? new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0)
                            : new BasicStroke(1));
                    int midY = y + line.height() / 2;
                    graphics.drawLine(MARGIN_DOTS, midY, paperWidthDots - MARGIN_DOTS, midY);
                    if (line.rule() == Rule.DOUBLE) {
                        graphics.drawLine(MARGIN_DOTS, midY + 3, paperWidthDots - MARGIN_DOTS, midY + 3);
                    }
                } else {
                    for (TextCell textCell : line.cells()) {
                        drawCell(graphics, textCell, y, line.height());
                    }
                }
                y += line.height();
            }
        } finally {
            graphics.dispose();
        }

        writeRasterImage(output, image);
    }

    private void writeRasterImage(ByteArrayOutputStream output, BufferedImage image) throws IOException {
        int bytesPerRow = (paperWidthDots + 7) / 8;
        int height = image.getHeight();
        output.write(new byte[]{
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
        if (cell.text().isEmpty()) {
            return;
        }
        TextLayout layout = new TextLayout(cell.text(), cell.font(), graphics.getFontRenderContext());
        float x = switch (cell.alignment()) {
            case LEFT -> cell.x();
            case CENTER -> cell.x() + (cell.width() - layout.getAdvance()) / 2;
            case RIGHT -> cell.x() + cell.width() - layout.getAdvance();
        };
        float baseline = top + (lineHeight - layout.getAscent() - layout.getDescent()) / 2 + layout.getAscent();
        layout.draw(graphics, x, baseline);
    }

    private boolean isBlack(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return red * 299 + green * 587 + blue * 114 < 150_000;
    }

    private float textWidth(String text, Font font) {
        return (float) font.getStringBounds(text, FONT_RENDER_CONTEXT).getWidth();
    }

    private TextCell cell(String text, int x, int width, Alignment alignment, Font font) {
        return new TextCell(text, x, width, alignment, font);
    }

    private Font font(int size, boolean bold) {
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size);
    }

    private int[] itemColumnWidths() {
        int contentWidth = contentWidth();
        int total = contentWidth * 19 / 100;
        int price = contentWidth * 19 / 100;
        int quantity = contentWidth * 15 / 100;
        return new int[]{total, price, quantity, contentWidth - total - price - quantity};
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
        DASHED,
        DOUBLE
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

        private void addBlank(int lineHeight) {
            addLine(List.of(), lineHeight);
        }

        private void addRule(boolean isDouble) {
            int lineHeight = isDouble ? 12 : 10;
            lines.add(new BlockLine(List.of(), lineHeight, isDouble ? Rule.DOUBLE : Rule.DASHED));
            height += lineHeight;
        }

        private void addTotalLine(String amount, String quantity, String label) {
            int contentWidth = contentWidth();
            addLine(List.of(
                    new TextCell(valueOrEmpty(amount), MARGIN_DOTS, contentWidth / 3, Alignment.LEFT, font(20, true)),
                    new TextCell(valueOrEmpty(quantity), MARGIN_DOTS + contentWidth / 3, contentWidth / 3, Alignment.CENTER, font(20, true)),
                    new TextCell(label, MARGIN_DOTS + 2 * contentWidth / 3, contentWidth / 3, Alignment.RIGHT, font(20, true))
            ), 32);
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
