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
    private String totalItemsCount;
    private String totalItemsAmount;
    private String discountAmount;
    private String netTotal;
    private String paidAmount;
    private String remainingAmount;

    @Override
    public String getType() {
        return "TOTALS";
    }
}
