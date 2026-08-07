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
    private String branch;
    private String phone;
    private String address;
    private String user;
    private String seller;
    private String receiptNumber;
    private String date;

    @Override
    public String getType() {
        return "HEADER";
    }
}
