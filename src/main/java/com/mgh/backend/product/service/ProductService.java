package com.mgh.backend.product.service;

import com.mgh.backend.cashier.dto.PageResponseDto;
import com.mgh.backend.product.dto.ProductSearchFilter;
import com.mgh.backend.product.dto.request.ProductManageSaveRequest;
import com.mgh.backend.product.dto.response.ProductDto;
import com.mgh.backend.product.dto.response.ProductIdResponse;
import com.mgh.backend.product.dto.response.ProductListItemDto;
import com.mgh.backend.product.dto.response.ProductManageDetailDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    PageResponseDto<ProductListItemDto> listProducts(ProductSearchFilter filter, Pageable pageable);

    ProductManageDetailDto getById(Long id);

    ProductIdResponse createProduct(@Valid ProductManageSaveRequest request);

    ProductIdResponse updateProduct(Long id, @Valid ProductManageSaveRequest request);

    void deleteProduct(Long id);

    // Internal integration
    ProductDto deductStockByCode(String code, int quantity);

    void restoreStockByCode(String code, int quantity);

    Integer getStockByCode(String code);
}
