package com.mgh.backend.cashier.port;

import java.math.BigDecimal;

public record CashierSaleProduct(
        String code,
        String name,
        BigDecimal price,
        int remainingStock
) {
}
