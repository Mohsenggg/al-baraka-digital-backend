package com.mgh.backend.product.service.impl;

import com.mgh.backend.cashier.port.CashierProductPort;
import com.mgh.backend.cashier.port.CashierSaleProduct;
import com.mgh.backend.product.dto.response.ProductDto;
import com.mgh.backend.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CashierProductPortImpl implements CashierProductPort {

    private final ProductService productService;

    @Override
    public CashierSaleProduct deductStock(String productCode, int quantity) {
        ProductDto product = productService.deductStockByBarcode(productCode, quantity);
        return new CashierSaleProduct(
                product.getBarcode(),
                product.getName(),
                product.getPrice(),
                product.getStock()
        );
    }

    @Override
    public void restoreStock(String productCode, int quantity) {
        productService.restoreStockByBarcode(productCode, quantity);
    }

    @Override
    public Integer getCurrentStock(String productCode) {
        return productService.getStockByBarcode(productCode);
    }
}
