package com.mgh.backend.cashier.printing.renderer;

import com.mgh.backend.cashier.entity.Cashier;
import com.mgh.backend.cashier.entity.Receipt;
import com.mgh.backend.cashier.entity.ReceiptItem;
import com.mgh.backend.cashier.printing.builder.ReceiptDocumentBuilder;
import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptLayoutConfig;
import com.mgh.backend.cashier.printing.model.elements.TotalsElement;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptEscPosRendererTest {

    @Test
    void testTotalPiecesSumMultipleItems() {
        ReceiptDocumentBuilder builder = new ReceiptDocumentBuilder();

        Cashier cashier = Cashier.builder()
                .id(3L)
                .username("محمود")
                .fullName("محمود جمال")
                .build();

        Receipt receipt = Receipt.builder()
                .receiptNumber("13530")
                .receiptDate(LocalDateTime.of(2026, 8, 2, 14, 4))
                .cashier(cashier)
                .items(List.of(
                        ReceiptItem.builder()
                                .productName("Product A")
                                .quantity(1.0)
                                .sellingPrice(BigDecimal.valueOf(85.00))
                                .total(BigDecimal.valueOf(85.00))
                                .build(),
                        ReceiptItem.builder()
                                .productName("Product B")
                                .quantity(1.0)
                                .sellingPrice(BigDecimal.valueOf(55.00))
                                .total(BigDecimal.valueOf(55.00))
                                .build(),
                        ReceiptItem.builder()
                                .productName("Product C")
                                .quantity(3.0)
                                .sellingPrice(BigDecimal.valueOf(20.00))
                                .total(BigDecimal.valueOf(60.00))
                                .build()
                ))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .build();

        ReceiptDocument doc = builder.build(receipt, ReceiptLayoutConfig.builder().build());

        TotalsElement totals = doc.getElements().stream()
                .filter(e -> e instanceof TotalsElement)
                .map(e -> (TotalsElement) e)
                .findFirst()
                .orElse(null);

        assertNotNull(totals);
        // Sum of quantities: 1 + 1 + 3 = 5
        assertEquals("5", totals.getTotalItemsCount());
        assertEquals("200.00", totals.getTotalItemsAmount());
        assertEquals("200.00", totals.getNetTotal());
    }

    @Test
    void testTotalPiecesSumSingleLargeQuantityItem() {
        ReceiptDocumentBuilder builder = new ReceiptDocumentBuilder();

        Receipt receipt = Receipt.builder()
                .receiptNumber("13531")
                .receiptDate(LocalDateTime.of(2026, 8, 2, 14, 10))
                .items(List.of(
                        ReceiptItem.builder()
                                .productName("Product A")
                                .quantity(40.0)
                                .sellingPrice(BigDecimal.valueOf(10.00))
                                .total(BigDecimal.valueOf(400.00))
                                .build()
                ))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .build();

        ReceiptDocument doc = builder.build(receipt, ReceiptLayoutConfig.builder().build());

        TotalsElement totals = doc.getElements().stream()
                .filter(e -> e instanceof TotalsElement)
                .map(e -> (TotalsElement) e)
                .findFirst()
                .orElse(null);

        assertNotNull(totals);
        // Sum of quantities: 40
        assertEquals("40", totals.getTotalItemsCount());
        assertEquals("400.00", totals.getTotalItemsAmount());
    }

    @Test
    void testHtmlRendererStructure() {
        ReceiptDocumentBuilder builder = new ReceiptDocumentBuilder();

        Cashier cashier = Cashier.builder()
                .id(3L)
                .username("محمود")
                .fullName("محمود جمال")
                .build();

        Receipt receipt = Receipt.builder()
                .receiptNumber("13530")
                .receiptDate(LocalDateTime.of(2026, 8, 2, 14, 4))
                .cashier(cashier)
                .items(List.of(
                        ReceiptItem.builder()
                                .productName("شاور فاريدا 650 مللى بجورا")
                                .quantity(1.0)
                                .sellingPrice(BigDecimal.valueOf(85.00))
                                .total(BigDecimal.valueOf(85.00))
                                .build(),
                        ReceiptItem.builder()
                                .productName("بخور انسام هرمى الاقصى")
                                .quantity(1.0)
                                .sellingPrice(BigDecimal.valueOf(55.00))
                                .total(BigDecimal.valueOf(55.00))
                                .build()
                ))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .build();

        ReceiptDocument doc = builder.build(receipt, ReceiptLayoutConfig.builder().build());

        ReceiptHtmlRenderer htmlRenderer = new ReceiptHtmlRenderer();
        String html = htmlRenderer.render(doc);

        assertNotNull(html);
        assertTrue(html.contains("dir=\"rtl\""));
        assertTrue(html.contains("البركة للمنظفات"));
        assertTrue(html.contains("الصنف"));
        assertTrue(html.contains("الكمية"));
        assertTrue(html.contains("السعر"));
        assertTrue(html.contains("الإجمالي"));
        assertTrue(html.contains("شاور فاريدا 650 مللى بجورا"));
        assertTrue(html.contains("بخور انسام هرمى الاقصى"));
        assertTrue(html.contains("إجمالي قطع :"));
        assertTrue(html.contains("صافى الفاتورة :"));
        assertTrue(html.contains("يسعدنا زيارتكم لنا بالمحل..."));
        assertTrue(html.contains("window.print()"));
    }

    @Test
    void testEscPosRendererRawCommandAndContent() throws IOException {
        ReceiptDocumentBuilder builder = new ReceiptDocumentBuilder();

        Cashier cashier = Cashier.builder()
                .id(3L)
                .username("محمود")
                .fullName("محمود جمال")
                .build();

        Receipt receipt = Receipt.builder()
                .receiptNumber("13530")
                .receiptDate(LocalDateTime.of(2026, 8, 2, 14, 4))
                .cashier(cashier)
                .items(List.of(
                        ReceiptItem.builder()
                                .productName("شاور فاريدا 650 مللى بجورا")
                                .quantity(1.0)
                                .sellingPrice(BigDecimal.valueOf(85.00))
                                .total(BigDecimal.valueOf(85.00))
                                .build()
                ))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .build();

        ReceiptDocument doc = builder.build(receipt, ReceiptLayoutConfig.builder().build());

        ReceiptEscPosRenderer renderer = new ReceiptEscPosRenderer();
        byte[] bytes = renderer.render(doc);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        // Verify that the bug [27, 116, 22] string literal is NOT present
        String outputStr = new String(bytes, StandardCharsets.ISO_8859_1);
        assertFalse(outputStr.contains("[27, 116, 22]"), "Must not contain literal [27, 116, 22] string!");

        // Verify binary ESC @ (0x1B, 0x40) and ESC t 22 (0x1B, 0x74, 0x16) are present at the beginning
        assertEquals(0x1B, bytes[0]);
        assertEquals(0x40, bytes[1]);
        assertEquals(0x1B, bytes[2]);
        assertEquals(0x74, bytes[3]);
        assertEquals(22, bytes[4]);
    }
}
