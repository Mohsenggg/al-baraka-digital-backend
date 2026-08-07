package com.mgh.backend.cashier.printing.model.elements;

import com.mgh.backend.cashier.printing.model.ReceiptElement;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SeparatorElement implements ReceiptElement {
    @Override
    public String getType() {
        return "SEPARATOR";
    }
}
