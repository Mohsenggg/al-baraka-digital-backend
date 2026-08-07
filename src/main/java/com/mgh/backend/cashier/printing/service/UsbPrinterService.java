package com.mgh.backend.cashier.printing.service;

import org.springframework.stereotype.Service;

import javax.print.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
public class UsbPrinterService {

    public void printReceipt(byte[] escPosData, String printerName) throws PrintException {
        DocFlavor flavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
        
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(flavor, null);
        PrintService targetService = null;
        
        for (PrintService service : printServices) {
            if (service.getName().equalsIgnoreCase(printerName)) {
                targetService = service;
                break;
            }
        }
        
        if (targetService == null) {
            targetService = PrintServiceLookup.lookupDefaultPrintService();
        }
        
        if (targetService == null) {
            throw new RuntimeException("No printer found");
        }

        try (InputStream is = new ByteArrayInputStream(escPosData)) {
            Doc doc = new SimpleDoc(is, flavor, null);
            DocPrintJob job = targetService.createPrintJob();
            job.print(doc, null);
        } catch (Exception e) {
            throw new PrintException("Failed to print receipt", e);
        }
    }
}
