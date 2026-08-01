package com.mgh.backend.cashier.port;

public interface CashierProductPort {

    CashierSaleProduct deductStock(String productCode, double quantity);

    void restoreStock(String productCode, double quantity);

    Double getCurrentStock(String productCode);

    com.mgh.backend.cashier.dto.RefillValidateResponse validateRefill(com.mgh.backend.cashier.dto.RefillValidateRequest request);

    com.mgh.backend.product.dto.response.ProductDto executeRefill(com.mgh.backend.cashier.dto.RefillExecuteRequest request);
}
