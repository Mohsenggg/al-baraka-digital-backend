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
public class HeaderElement implements ReceiptElement {
    private String storeName;
    private String storeAddress;
    private String storePhone;
    private String cashierName;
    private String receiptDate;
    private String receiptNumber;

    @Override
    public String getType() {
        return "HEADER";
    }
}
