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
    private String cashierName;
    private String receiptNumber;
    private String date;
    private String time;

    public String getCashierName() {
        if (cashierName != null && !cashierName.isBlank()) {
            return cashierName;
        }
        if (user != null && !user.isBlank()) {
            int dashIdx = user.indexOf('-');
            if (dashIdx >= 0 && dashIdx + 1 < user.length()) {
                return user.substring(dashIdx + 1).trim();
            }
            return user;
        }
        return seller != null ? seller : "";
    }

    @Override
    public String getType() {
        return "HEADER";
    }
}
