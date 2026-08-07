package com.mgh.backend.cashier.printing.model.elements;

import com.mgh.backend.cashier.printing.model.ReceiptElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerElement implements ReceiptElement {
    private String customerName;
    private String customerPhone;

    @Override
    public String getType() {
        return "CUSTOMER";
    }
}
