package com.mgh.backend.product.service;

import com.mgh.backend.cashier.dto.PageResponseDto;
import com.mgh.backend.product.dto.ProductSearchFilter;
import com.mgh.backend.product.dto.request.AddBarcodeRequest;
import com.mgh.backend.product.dto.request.CreateProductRequest;
import com.mgh.backend.product.dto.request.CreateReferenceDataRequest;
import com.mgh.backend.product.dto.request.ProductManageSaveRequest;
import com.mgh.backend.product.dto.request.ProductStatusUpdateRequest;
import com.mgh.backend.product.dto.request.UpdateProductRequest;
import com.mgh.backend.product.dto.response.ProductBarcodeDto;
import com.mgh.backend.product.dto.response.ProductDto;
import com.mgh.backend.product.dto.response.ProductListItemDto;
import com.mgh.backend.product.dto.response.ProductManageDetailDto;
import com.mgh.backend.product.dto.response.ProductReferenceDataDto;
import com.mgh.backend.product.dto.response.ProductStatusUpdateResponse;
import com.mgh.backend.product.dto.response.ReferenceItemDto;
import com.mgh.backend.product.dto.response.StockSummaryDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    PageResponseDto<ProductListItemDto> listProducts(ProductSearchFilter filter, Pageable pageable);

    List<ProductListItemDto> quickSearch(String query, int limit);

    ProductListItemDto getById(Long id);

    ProductListItemDto getByCode(String code);

    ProductListItemDto getByBarcode(String barcode);

    ProductManageDetailDto getDetail(Long id);

    ProductManageDetailDto createDetail(@Valid ProductManageSaveRequest request);

    ProductManageDetailDto updateDetail(Long id, @Valid ProductManageSaveRequest request);

    void softDeleteById(Long id);

    ProductStatusUpdateResponse updateStatus(Long id, @Valid ProductStatusUpdateRequest request);

    List<ProductBarcodeDto> listBarcodes(Long productId);

    ProductBarcodeDto addBarcode(Long productId, @Valid AddBarcodeRequest request);

    void deleteBarcode(Long productId, Long barcodeId);

    ProductReferenceDataDto getReferenceData();

    ReferenceItemDto createReferenceData(String type, @Valid CreateReferenceDataRequest request);

    StockSummaryDto getStockSummary(Long productId);

    List<ProductDto> getAllProductsForCashier();

    ProductDto createLegacyProduct(@Valid CreateProductRequest request);

    ProductDto updateLegacyProduct(Long id, @Valid UpdateProductRequest request);

    ProductDto deductStockByCode(String code, int quantity);

    void restoreStockByCode(String code, int quantity);

    Integer getStockByCode(String code);
}
