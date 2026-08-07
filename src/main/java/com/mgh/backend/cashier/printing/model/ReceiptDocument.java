package com.mgh.backend.cashier.printing.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReceiptDocument {
    private List<ReceiptElement> elements = new ArrayList<>();

    public void addElement(ReceiptElement element) {
        if (element != null) {
            elements.add(element);
        }
    }
}
