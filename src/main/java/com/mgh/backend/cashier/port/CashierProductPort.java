package com.mgh.backend.cashier.port;

public interface CashierProductPort {

    CashierSaleProduct deductStock(String productCode, int quantity);

    void restoreStock(String productCode, int quantity);

    Integer getCurrentStock(String productCode);
}
