package com.mgh.backend.cashier.service;

import com.mgh.backend.cashier.dto.CreateProductRequest;
import com.mgh.backend.cashier.dto.ProductDto;
import com.mgh.backend.cashier.dto.UpdateProductRequest;
import jakarta.validation.Valid;

import java.util.List;

public interface ProductService {

    List<ProductDto> searchProducts(String query);
    List<ProductDto> getAllProducts();

    ProductDto getProductByCode(String code);

    ProductDto createProduct(@Valid CreateProductRequest request);

    ProductDto updateProduct(String code, @Valid UpdateProductRequest request);

    void deleteProductByCode(String code);


}