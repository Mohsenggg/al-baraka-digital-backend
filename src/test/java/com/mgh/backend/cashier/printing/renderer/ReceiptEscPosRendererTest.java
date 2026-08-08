package com.mgh.backend.cashier.printing.renderer;

import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.elements.HeaderElement;
import com.mgh.backend.cashier.printing.model.elements.ItemsTableElement;
import com.mgh.backend.cashier.printing.model.elements.TotalsElement;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptEscPosRendererTest {

    @Test
    void testRenderAndSaveToFile() throws IOException {
        ReceiptEscPosRenderer renderer = new ReceiptEscPosRenderer();
        ReceiptDocument doc = new ReceiptDocument();

        doc.addElement(HeaderElement.builder()
                .storeName("البركة للمنظفات")
                .branch("البركة للمنظفات")
                .phone("01065527862")
                .address("شارع الكيلانى")
                .user("محمود جمال - 3")
                .seller("محمود")
                .receiptNumber("13530")
                .date("02/08/2026 2:04 pm")
                .build());

        doc.addElement(ItemsTableElement.builder()
                .items(List.of(
                        ItemsTableElement.ItemRow.builder()
                                .name("شاور فاريدا 650 مللى بجورا")
                                .quantity("1")
                                .unitPrice("85.00")
                                .totalPrice("85.00")
                                .build(),
                        ItemsTableElement.ItemRow.builder()
                                .name("بخور انسام هرمى الاقصى")
                                .quantity("1")
                                .unitPrice("55.00")
                                .totalPrice("55.00")
                                .build()
                ))
                .build());

        doc.addElement(TotalsElement.builder()
                .totalItemsCount("2")
                .totalItemsAmount("140.00")
                .discountAmount("0.00")
                .netTotal("140.00")
                .paidAmount("0.00")
                .remainingAmount("0.00")
                .build());

        byte[] output = renderer.render(doc);
        assertNotNull(output);
        assertTrue(output.length > 0);

        // Saves to the project root directory during test run
        try (FileOutputStream fos = new FileOutputStream("receipt_output.bin")) {
            fos.write(output);
        }
    }
}
