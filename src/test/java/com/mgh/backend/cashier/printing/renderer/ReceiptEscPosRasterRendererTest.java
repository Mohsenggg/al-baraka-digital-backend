package com.mgh.backend.cashier.printing.renderer;

import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.elements.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptEscPosRasterRendererTest {

    @Test
    void rendersArabicReceiptAs58MillimeterRasterAndKeepsBarcode() throws IOException {
        byte[] output = new ReceiptEscPosRenderer(58).render(receiptDocument());

        assertArrayEquals(new byte[]{0x1B, 0x40}, new byte[]{output[0], output[1]});
        int rasterOffset = findRasterCommand(output);
        assertTrue(rasterOffset >= 0);
        assertEquals(48, unsigned(output[rasterOffset + 4]));
        assertTrue(unsigned(output[rasterOffset + 6]) > 0);
        assertTrue(output.length > 500);
        assertTrue(contains(output, new byte[]{0x1D, 0x6B}));
        assertFalse(contains(output, new byte[]{0x1B, 0x74, 22}),
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
    void rejectsUnsupportedPaperWidth() {
        assertThrows(IllegalArgumentException.class, () -> new ReceiptEscPosRenderer(76));
    }

    private ReceiptDocument receiptDocument() {
        ReceiptDocument document = new ReceiptDocument();
        document.addElement(HeaderElement.builder()
                .storeName("البركة للمنظفات")
                .branch("البركة للمنظفات")
                .phone("01065527862")
                .address("شارع الكيلانى")
                .user("3 - محمود جمال")
                .seller("default_cashier")
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
                        .build()
        )));
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
