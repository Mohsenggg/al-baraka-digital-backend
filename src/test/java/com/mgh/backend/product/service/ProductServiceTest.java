package com.mgh.backend.product.service;

import com.mgh.backend.cashier.exception.ResourceNotFoundException;
import com.mgh.backend.product.dto.request.ProductManageSaveRequest;
import com.mgh.backend.product.dto.response.ProductIdResponse;
import com.mgh.backend.product.entity.*;
import com.mgh.backend.product.mapper.ProductMapper;
import com.mgh.backend.product.repository.*;
import com.mgh.backend.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductBarcodeRepository productBarcodeRepository;

    @Mock
    private ProductCategoryRepository categoryRepository;

    @Mock
    private ManufacturerRepository manufacturerRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductAttributeRepository attributeRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    private ProductGroup group;
    private Product product1;
    private Product product2;
    private ProductBarcode barcode1;
    private ProductBarcode barcode2;

    @BeforeEach
    void setUp() {
        group = ProductGroup.builder()
                .id(10L)
                .name("Ariel 1kg Group")
                .code("GRP-AR-1")
                .isPriceUnified(true)
                .build();

        barcode1 = ProductBarcode.builder()
                .id(101L)
                .barcode("622111111")
                .sellingPrice(new BigDecimal("60.00"))
                .buyingPrice(new BigDecimal("45.00"))
                .stock(10.0)
                .isDefault(true)
                .build();

        product1 = Product.builder()
                .id(1L)
                .name("Ariel Lavender 1kg")
                .baseName("Ariel Lavender")
                .type(ProductType.INVENTORY)
                .status(ProductStatus.ACTIVE)
                .productGroup(group)
                .barcodes(new ArrayList<>(List.of(barcode1)))
                .attributeValues(new ArrayList<>())
                .suppliers(new ArrayList<>())
                .conversions(new ArrayList<>())
                .materials(new ArrayList<>())
                .build();
        barcode1.setProduct(product1);

        barcode2 = ProductBarcode.builder()
                .id(102L)
                .barcode("622222222")
                .sellingPrice(new BigDecimal("60.00"))
                .buyingPrice(new BigDecimal("48.00"))
                .stock(20.0)
                .isDefault(true)
                .build();

        product2 = Product.builder()
                .id(2L)
                .name("Ariel Downy 1kg")
                .baseName("Ariel Downy")
                .type(ProductType.INVENTORY)
                .status(ProductStatus.ACTIVE)
                .productGroup(group)
                .barcodes(new ArrayList<>(List.of(barcode2)))
                .attributeValues(new ArrayList<>())
                .suppliers(new ArrayList<>())
                .conversions(new ArrayList<>())
                .materials(new ArrayList<>())
                .build();
        barcode2.setProduct(product2);
    }

    @Test
    @DisplayName("updateProduct: Updates only single product when propagateGroupSellingPrice is false")
    void updateProduct_NoPropagation() {
        ProductManageSaveRequest request = new ProductManageSaveRequest();
        request.setBaseName("Ariel Lavender");
        request.setName("Ariel Lavender 1kg");

        ProductManageSaveRequest.BarcodeInput bi = new ProductManageSaveRequest.BarcodeInput();
        bi.setId(101L);
        bi.setBarcode("622111111");
        bi.setSellingPrice(new BigDecimal("70.00"));
        bi.setBuyingPrice(new BigDecimal("45.00"));
        bi.setStock(10.0);
        bi.setDefault(true);
        request.setBarcodes(List.of(bi));

        when(productRepository.findDetailedById(1L)).thenReturn(Optional.of(product1));
        when(productRepository.save(product1)).thenReturn(product1);

        ProductIdResponse response = productService.updateProduct(1L, request, false);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        verify(productRepository, never()).findAllWithBarcodesByProductGroupIdAndDeletedAtIsNull(any());
        // product2 was not touched
        assertEquals(new BigDecimal("60.00"), barcode2.getSellingPrice());
    }

    @Test
    @DisplayName("updateProduct: Propagates selling price to all products in group, keeps buying price untouched")
    void updateProduct_WithPropagation() {
        ProductManageSaveRequest request = new ProductManageSaveRequest();
        request.setBaseName("Ariel Lavender");
        request.setName("Ariel Lavender 1kg");

        ProductManageSaveRequest.BarcodeInput bi = new ProductManageSaveRequest.BarcodeInput();
        bi.setId(101L);
        bi.setBarcode("622111111");
        bi.setSellingPrice(new BigDecimal("75.00"));
        bi.setBuyingPrice(new BigDecimal("45.00"));
        bi.setStock(10.0);
        bi.setDefault(true);
        request.setBarcodes(List.of(bi));

        when(productRepository.findDetailedById(1L)).thenReturn(Optional.of(product1));
        when(productRepository.save(product1)).thenReturn(product1);

        // Barcode mapping for propagation
        barcode1.setSellingPrice(new BigDecimal("75.00"));
        when(productMapper.activeBarcodes(product1)).thenReturn(List.of(barcode1));
        when(productMapper.findDefaultBarcode(List.of(barcode1))).thenReturn(barcode1);

        when(productRepository.findAllWithBarcodesByProductGroupIdAndDeletedAtIsNull(10L))
                .thenReturn(List.of(product1, product2));
        when(productMapper.activeBarcodes(product2)).thenReturn(List.of(barcode2));

        ProductIdResponse response = productService.updateProduct(1L, request, true);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        // product2 selling price was updated to 75.00
        assertEquals(new BigDecimal("75.00"), barcode2.getSellingPrice());
        // product2 buying price remained 48.00 (untouched!)
        assertEquals(new BigDecimal("48.00"), barcode2.getBuyingPrice());
        verify(productRepository, times(1)).saveAll(anyList());
    }
}
