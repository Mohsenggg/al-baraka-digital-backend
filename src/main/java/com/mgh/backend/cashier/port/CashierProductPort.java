package com.mgh.backend.cashier.port;

public interface CashierProductPort {

    CashierSaleProduct deductStock(String productCode, double quantity);

    void restoreStock(String productCode, double quantity);

    Double getCurrentStock(String productCode);
}
