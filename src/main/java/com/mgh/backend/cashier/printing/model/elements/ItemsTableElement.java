package com.mgh.backend.cashier.printing.model.elements;

import com.mgh.backend.cashier.printing.model.ReceiptElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemsTableElement implements ReceiptElement {
    private List<ItemRow> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRow {
        private String name;
        private String quantity;
        private String unitPrice;
        private String totalPrice;
    }

    @Override
    public String getType() {
        return "ITEMS_TABLE";
    }
}
