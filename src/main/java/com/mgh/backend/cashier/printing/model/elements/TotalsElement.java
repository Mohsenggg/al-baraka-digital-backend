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
public class TotalsElement implements ReceiptElement {
    private String subtotal;
    private String taxAmount;
    private String discountAmount;
    private String grandTotal;

    @Override
    public String getType() {
        return "TOTALS";
    }
}
