package com.mgh.backend.product.service;

import com.mgh.backend.product.dto.CreateProductRequest;
import com.mgh.backend.product.dto.ProductDto;
import com.mgh.backend.product.dto.UpdateProductRequest;
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
