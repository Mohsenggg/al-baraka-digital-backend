package com.mgh.backend.cashier.printing.renderer;

import com.mgh.backend.cashier.entity.Cashier;
import com.mgh.backend.cashier.entity.Receipt;
import com.mgh.backend.cashier.printing.builder.ReceiptDocumentBuilder;
import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptLayoutConfig;
import com.mgh.backend.cashier.printing.model.elements.*;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptEscPosRasterRendererTest {

    @Test
    void rendersArabicReceiptAs58MillimeterRasterAndKeepsBarcode() throws IOException {
        byte[] output = new ReceiptEscPosRenderer(58).render(receiptDocument());

        assertArrayEquals(new byte[] { 0x1B, 0x40 }, new byte[] { output[0], output[1] });
        int rasterOffset = findRasterCommand(output);
        assertTrue(rasterOffset >= 0);
        assertEquals(48, unsigned(output[rasterOffset + 4]));
        assertTrue(unsigned(output[rasterOffset + 6]) > 0);
        assertTrue(output.length > 500);
        assertTrue(contains(output, new byte[] { 0x1D, 0x6B }));
        assertFalse(contains(output, new byte[] { 0x1B, 0x74, 22 }),
                "Text should be rasterized instead of relying on the printer's Arabic code page");
    }

    @Test
    void uses80MillimeterRasterWidthWhenConfigured() throws IOException {
        byte[] output = new ReceiptEscPosRenderer(80).render(receiptDocument());
        int rasterOffset = findRasterCommand(output);

        assertTrue(rasterOffset >= 0);
        assertEquals(72, unsigned(output[rasterOffset + 4]));
    }

    @Test
    void previewUsesConfiguredPaperWidth() {
        String html = new ReceiptHtmlRenderer(58).render(receiptDocument());

        assertTrue(html.contains("معاينة الفاتورة - 58mm"));
        assertTrue(html.contains("size: 58mm auto"));
        assertTrue(html.contains("width: 58mm"));
        assertFalse(html.contains("80mm"));
    }

    @Test
    void previewContainsTwoColumnHeaderAndEmphasizedTotalsAndBottomBarcode() {
        String html = new ReceiptHtmlRenderer(80).render(receiptDocument());

        assertTrue(html.contains("receipt-header-grid"));
        assertTrue(html.contains("header-row"));
        assertTrue(html.contains("الفرع:"));
        assertTrue(html.contains("الهاتف:"));
        assertTrue(html.contains("العنوان:"));
        assertTrue(html.contains("الكاشير:"));
        assertTrue(html.contains("محمود جمال"));
        assertTrue(html.contains("رقم الفاتورة:"));
        assertTrue(html.contains("التاريخ:"));

        // Barcode at the end (after footer)
        int footerIndex = html.indexOf("receipt-footer");
        int barcodeIndex = html.indexOf("barcode-container");
        assertTrue(footerIndex >= 0 && barcodeIndex > footerIndex, "Barcode should be at the end after footer");

        // Emphasized final total
        assertTrue(html.contains("final-total-box"));
        assertTrue(html.contains("صافى الفاتورة :"));
        assertTrue(html.contains("table-layout: fixed"));
    }

    @Test
    void handlesLongProductNamesDifferentPriceLengthsAndVaryingHeaderInfoWithoutOverflow() throws IOException {
        ReceiptDocument stressedDoc = new ReceiptDocument();
        stressedDoc.addElement(HeaderElement.builder()
                .storeName("البركة للمنظفات ومستحضرات التجميل")
                .branch("فرع شارع الكيلانى الرئيسى")
                .phone("01065527862")
                .address("شارع الكيلانى بجوار المحطة")
                .cashierName("عبد الرحمن محمد محمود")
                .receiptNumber("INV-20261114-B9876")
                .date("14/11/2026")
                .time("11:45 pm")
                .build());
        stressedDoc.addElement(new SeparatorElement());
        stressedDoc.addElement(new ItemsTableElement(List.of(
                ItemsTableElement.ItemRow.builder()
                        .name("شاور جيل فاريدا 650 مللى برائحة الخوخ والمانجو")
                        .quantity("1")
                        .unitPrice("5.00")
                        .totalPrice("5.00")
                        .build(),
                ItemsTableElement.ItemRow.builder()
                        .name("مسحوق غسيل اوتوماتيك اكسترا سوبر جامبو توفير 10 كيلو جرام")
                        .quantity("25")
                        .unitPrice("450.50")
                        .totalPrice("11262.50")
                        .build(),
                ItemsTableElement.ItemRow.builder()
                        .name("صنف_بدون_مسافات_طويل_جدا_لاختبار_التفاف_النص_داخل_العمود_بنجاح")
                        .quantity("100.25")
                        .unitPrice("1250.00")
                        .totalPrice("125312.50")
                        .build())));
        stressedDoc.addElement(new SeparatorElement());
        stressedDoc.addElement(TotalsElement.builder()
                .totalItemsCount("126.25")
                .totalItemsAmount("136580.00")
                .discountAmount("580.00")
                .netTotal("136000.00")
                .paidAmount("140000.00")
                .remainingAmount("4000.00")
                .build());
        stressedDoc.addElement(new SeparatorElement());
        stressedDoc.addElement(new FooterElement("يسعدنا زيارتكم لنا بالمحل - خدمة العملاء متاحة طوال الأسبوع"));

        ReceiptEscPosRenderer escPosRenderer = new ReceiptEscPosRenderer(80);
        BufferedImage image = escPosRenderer.renderToImage(stressedDoc);
        assertEquals(576, image.getWidth());
        assertTrue(image.getHeight() > 300);

        // Verify left and right outer margins (8 dots each) remain completely white (no
        // horizontal overflow)
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(0xFFFFFF, image.getRGB(x, y) & 0xFFFFFF,
                        "Left margin overflow detected at (" + x + "," + y + ")");
            }
            for (int x = 576 - 8; x < 576; x++) {
                assertEquals(0xFFFFFF, image.getRGB(x, y) & 0xFFFFFF,
                        "Right margin overflow detected at (" + x + "," + y + ")");
            }
        }

        // Verify vertical blank padding between Store Name and Header (y=34..49)
        for (int y = 34; y < 50; y++) {
            for (int x = 0; x < 576; x++) {
                assertEquals(0xFFFFFF, image.getRGB(x, y) & 0xFFFFFF,
                        "Expected blank vertical spacing between store name and header at (" + x + "," + y + ")");
            }
        }

        // Verify each of the 3 header rows (y=50..73, 74..97, 98..121) has a clear
        // white gap separating left & right columns
        int[][] headerRowRanges = { { 50, 73 }, { 74, 97 }, { 98, 121 } };
        for (int r = 0; r < headerRowRanges.length; r++) {
            int yStart = headerRowRanges[r][0];
            int yEnd = headerRowRanges[r][1];
            int maxConsecutiveWhiteCols = 0;
            int currentConsecutive = 0;
            for (int x = 140; x <= 430; x++) {
                boolean colAllWhite = true;
                for (int y = yStart; y <= yEnd; y++) {
                    if ((image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                        colAllWhite = false;
                        break;
                    }
                }
                if (colAllWhite) {
                    currentConsecutive++;
                    maxConsecutiveWhiteCols = Math.max(maxConsecutiveWhiteCols, currentConsecutive);
                } else {
                    currentConsecutive = 0;
                }
            }
            assertTrue(maxConsecutiveWhiteCols >= 10,
                    "Header row " + (r + 1) + " columns should have at least 10px of white separation, found: "
                            + maxConsecutiveWhiteCols);
        }

        // Verify vertical blank padding between Header and Item section around the dashed divider (y=122..131 and y=144..151)
        for (int y = 122; y < 132; y++) {
            for (int x = 0; x < 576; x++) {
                assertEquals(0xFFFFFF, image.getRGB(x, y) & 0xFFFFFF,
                        "Expected blank vertical spacing after header at (" + x + "," + y + ")");
            }
        }
        for (int y = 144; y < 152; y++) {
            for (int x = 0; x < 576; x++) {
                assertEquals(0xFFFFFF, image.getRGB(x, y) & 0xFFFFFF,
                        "Expected blank vertical spacing before items header at (" + x + "," + y + ")");
            }
        }

        // Verify item table column boundaries (x=107..109, x=207..209, x=274..277)
        // across all item rows (y=188..337) remain completely white (no column overlap)
        int[] gapXs = { 107, 108, 207, 208, 274, 275, 276, 277 };
        for (int y = 188; y < 338; y++) {
            for (int gx : gapXs) {
                assertEquals(0xFFFFFF, image.getRGB(gx, y) & 0xFFFFFF,
                        "Item column overlap detected at (" + gx + "," + y + ")");
            }
        }

        // Verify full byte stream also renders cleanly
        byte[] escPosBytes = escPosRenderer.render(stressedDoc);
        assertTrue(escPosBytes.length > 1000);
        assertTrue(contains(escPosBytes, new byte[] { 0x1D, 0x6B }));

        // Verify HTML preview also renders all stressed items cleanly
        String html = new ReceiptHtmlRenderer(80).render(stressedDoc);
        assertTrue(html.contains("عبد الرحمن محمد محمود"));
        assertTrue(html.contains("INV-20261114-B9876"));
        assertTrue(html.contains("136000.00"));
    }

    @Test
    void cashierFullNameIsUsedInsteadOfLoginUsername() {
        Cashier cashier = Cashier.builder()
                .id(5L)
                .username("cashier_login_user")
                .fullName("أحمد محمود علي")
                .build();

        Receipt receipt = Receipt.builder()
                .receiptNumber("INV-999")
                .cashier(cashier)
                .build();

        ReceiptDocument doc = new ReceiptDocumentBuilder().build(receipt, ReceiptLayoutConfig.builder().build());
        HeaderElement header = (HeaderElement) doc.getElements().get(0);

        assertEquals("أحمد محمود علي", header.getCashierName());
        assertNotEquals("cashier_login_user", header.getCashierName());
    }

    @Test
    void rejectsUnsupportedPaperWidth() {
        assertThrows(IllegalArgumentException.class, () -> new ReceiptEscPosRenderer(76));
    }

    private ReceiptDocument receiptDocument() {
        ReceiptDocument document = new ReceiptDocument();
        document.addElement(HeaderElement.builder()
                .storeName("البركة للمنظفات")
                .branch("فرع مطاوع")
                .phone("01065527862")
                .address("شارع الكيلانى")
                .user("3 - محمود جمال")
                .seller("محمود")
                .receiptNumber("INV-20261031-B427")
                .date("31/10/2026")
                .time("10:38 pm")
                .build());
        document.addElement(new SeparatorElement());
        document.addElement(new ItemsTableElement(List.of(
                ItemsTableElement.ItemRow.builder()
                        .name("منظف عربي 500 مللي")
                        .quantity("1")
                        .unitPrice("79.00")
                        .totalPrice("79.00")
                        .build())));
        document.addElement(new SeparatorElement());
        document.addElement(TotalsElement.builder()
                .totalItemsCount("1")
                .totalItemsAmount("79.00")
                .discountAmount("0.00")
                .netTotal("79.00")
                .paidAmount("0.00")
                .remainingAmount("0.00")
                .build());
        document.addElement(new FooterElement("يسعدنا زيارتكم لنا بالمحل..."));
        return document;
    }

    private int findRasterCommand(byte[] data) {
        for (int i = 0; i <= data.length - 8; i++) {
            if (data[i] == 0x1D && data[i + 1] == 0x76 && data[i + 2] == 0x30 && data[i + 3] == 0) {
                return i;
            }
        }
        return -1;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private boolean contains(byte[] data, byte[] sequence) {
        for (int i = 0; i <= data.length - sequence.length; i++) {
            boolean match = true;
            for (int j = 0; j < sequence.length; j++) {
                if (data[i + j] != sequence[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
