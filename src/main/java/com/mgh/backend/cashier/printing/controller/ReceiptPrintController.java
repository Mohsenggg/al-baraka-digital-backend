package com.mgh.backend.cashier.printing.controller;

import com.mgh.backend.cashier.entity.Receipt;
import com.mgh.backend.cashier.printing.builder.ReceiptDocumentBuilder;
import com.mgh.backend.cashier.printing.model.ReceiptDocument;
import com.mgh.backend.cashier.printing.model.ReceiptLayoutConfig;
import com.mgh.backend.cashier.printing.renderer.ReceiptHtmlRenderer;
import com.mgh.backend.cashier.printing.service.UsbPrinterService;
import com.mgh.backend.cashier.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/receipt")
@RequiredArgsConstructor
public class ReceiptPrintController {

    private final ReceiptRepository receiptRepository;
    private final ReceiptDocumentBuilder documentBuilder;
    private final ReceiptHtmlRenderer htmlRenderer;

    private final UsbPrinterService usbPrinterService;

    @Value("${app.printer.name:XP-D200N}")
    private String printerName;

    @GetMapping(value = "/{id}/preview", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> previewReceipt(@PathVariable Long id) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Receipt not found"));
                
        ReceiptLayoutConfig config = ReceiptLayoutConfig.builder().build();
        ReceiptDocument document = documentBuilder.build(receipt, config);
        String html = htmlRenderer.render(document);
        
        return ResponseEntity.ok(html);
    }

    @PostMapping("/{id}/print")
    @Transactional(readOnly = true)
    public ResponseEntity<Void> printReceipt(@PathVariable Long id) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Receipt not found"));

        ReceiptLayoutConfig config = ReceiptLayoutConfig.builder().build();
        ReceiptDocument document = documentBuilder.build(receipt, config);

        try {
            byte[] escPosData = new com.mgh.backend.cashier.printing.renderer.ReceiptEscPosRenderer().render(document);
            usbPrinterService.printReceipt(escPosData, printerName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to print receipt", e);
        }
    }
}
