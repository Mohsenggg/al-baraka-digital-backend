package com.mgh.backend.product.migration;

import com.mgh.backend.product.entity.Brand;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.ProductGroup;
import com.mgh.backend.product.repository.BrandRepository;
import com.mgh.backend.product.repository.ProductBarcodeRepository;
import com.mgh.backend.product.repository.ProductCategoryRepository;
import com.mgh.backend.product.repository.ProductGroupRepository;
import com.mgh.backend.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PosMigrationServiceTest {

    @Mock
    private ProductCategoryRepository categoryRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private ProductGroupRepository productGroupRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductBarcodeRepository productBarcodeRepository;

    @InjectMocks
    private PosMigrationServiceImpl posMigrationService;

    @BeforeEach
    public void setUp() {
        // Setup default behavior for saveAll to return input list
        lenient().when(categoryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(brandRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productGroupRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    public void testSuccessfulMigration() {
        // Prepare input data matching: Category -> Brand -> ProductGroup -> Product
        List<PosDataItemDto> items = new ArrayList<>();

        // Category
        PosDataItemDto catItem = new PosDataItemDto();
        catItem.setItemCode("01");
        catItem.setItemPrnt("0");
        catItem.setItemName("مساحيق");
        catItem.setItemType(1);
        items.add(catItem);

        // Brand
        PosDataItemDto brandItem = new PosDataItemDto();
        brandItem.setItemCode("0101");
        brandItem.setItemPrnt("01");
        brandItem.setItemName("شركة اريال");
        brandItem.setItemType(1);
        items.add(brandItem);

        // ProductGroup
        PosDataItemDto pgItem = new PosDataItemDto();
        pgItem.setItemCode("01016");
        pgItem.setItemPrnt("0101");
        pgItem.setItemName("اريال 1ك");
        pgItem.setItemType(1);
        items.add(pgItem);

        // Product
        PosDataItemDto prodItem = new PosDataItemDto();
        prodItem.setItemCode("8006540852170");
        prodItem.setItemPrnt("01016");
        prodItem.setItemName("اريال 1كيلو لافندر");
        prodItem.setItemType(2);
        prodItem.setItemPrice0(new BigDecimal("100.00")); // Buying price
        prodItem.setItemPrice1(new BigDecimal("120.00")); // Selling price
        prodItem.setItemMinStock(5.0);
        prodItem.setItemMaxStock(100.0);
        items.add(prodItem);

        // Mock empty database
        when(categoryRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(brandRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(productGroupRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(productRepository.findAllProductBarcodes()).thenReturn(Collections.emptySet());
        when(productBarcodeRepository.findAllBarcodeBarcodes()).thenReturn(Collections.emptySet());

        // Execute migration
        MigrationResultDto result = posMigrationService.importPosData(items);

        // Verify result DTO
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getCategoriesCreated());
        assertEquals(1, result.getBrandsCreated());
        assertEquals(1, result.getProductGroupsCreated());
        assertEquals(1, result.getProductsCreated());
        assertEquals(4, result.getTotalRecordsProcessed());

        // Verify saved structures via ArgumentCaptors
        ArgumentCaptor<List<ProductCategory>> catCaptor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(catCaptor.capture());
        List<ProductCategory> savedCats = catCaptor.getValue();
        assertEquals(1, savedCats.size());
        assertEquals("01", savedCats.get(0).getCode());
        assertEquals("مساحيق", savedCats.get(0).getName());

        ArgumentCaptor<List<Brand>> brandCaptor = ArgumentCaptor.forClass(List.class);
        verify(brandRepository).saveAll(brandCaptor.capture());
        List<Brand> savedBrands = brandCaptor.getValue();
        assertEquals(1, savedBrands.size());
        assertEquals("0101", savedBrands.get(0).getCode());
        assertEquals("01", savedBrands.get(0).getCategory().getCode());

        ArgumentCaptor<List<ProductGroup>> pgCaptor = ArgumentCaptor.forClass(List.class);
        verify(productGroupRepository).saveAll(pgCaptor.capture());
        List<ProductGroup> savedPgs = pgCaptor.getValue();
        assertEquals(1, savedPgs.size());
        assertEquals("01016", savedPgs.get(0).getCode());
        assertEquals("0101", savedPgs.get(0).getBrand().getCode());

        ArgumentCaptor<List<Product>> prodCaptor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(prodCaptor.capture());
        List<Product> savedProds = prodCaptor.getValue();
        assertEquals(1, savedProds.size());
        Product prod = savedProds.get(0);
        assertEquals("8006540852170", prod.getBarcode());
        assertEquals("اريال 1كيلو لافندر", prod.getName());
        assertEquals("01016", prod.getProductGroup().getCode());
        assertEquals("01", prod.getCategory().getCode()); // category link verified!
        assertEquals(5.0, prod.getMinStockLevel());
        assertEquals(100.0, prod.getMaxStockLevel());

        // Verify default barcode
        assertEquals(1, prod.getBarcodes().size());
        ProductBarcode pb = prod.getBarcodes().get(0);
        assertEquals("8006540852170", pb.getBarcode());
        assertEquals(new BigDecimal("100.00"), pb.getBuyingPrice());
        assertEquals(new BigDecimal("120.00"), pb.getSellingPrice());
        assertTrue(pb.isDefault());
    }

    @Test
    public void testMigrationWithArbitraryOrder() {
        // Items in arbitrary order: ProductGroup -> Category -> Product -> Brand
        List<PosDataItemDto> items = new ArrayList<>();

        PosDataItemDto pgItem = new PosDataItemDto();
        pgItem.setItemCode("01016");
        pgItem.setItemPrnt("0101");
        pgItem.setItemName("اريال 1ك");
        pgItem.setItemType(1);
        items.add(pgItem);

        PosDataItemDto catItem = new PosDataItemDto();
        catItem.setItemCode("01");
        catItem.setItemPrnt("0");
        catItem.setItemName("مساحيق");
        catItem.setItemType(1);
        items.add(catItem);

        PosDataItemDto prodItem = new PosDataItemDto();
        prodItem.setItemCode("8006540852170");
        prodItem.setItemPrnt("01016");
        prodItem.setItemName("اريال 1كيلو لافندر");
        prodItem.setItemType(2);
        items.add(prodItem);

        PosDataItemDto brandItem = new PosDataItemDto();
        brandItem.setItemCode("0101");
        brandItem.setItemPrnt("01");
        brandItem.setItemName("شركة اريال");
        brandItem.setItemType(1);
        items.add(brandItem);

        when(categoryRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(brandRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(productGroupRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(productRepository.findAllProductBarcodes()).thenReturn(Collections.emptySet());
        when(productBarcodeRepository.findAllBarcodeBarcodes()).thenReturn(Collections.emptySet());

        // Execute migration - should successfully build and resolve hierarchy despite order
        MigrationResultDto result = posMigrationService.importPosData(items);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getCategoriesCreated());
        assertEquals(1, result.getBrandsCreated());
        assertEquals(1, result.getProductGroupsCreated());
        assertEquals(1, result.getProductsCreated());
    }

    @Test
    public void testDuplicateItemCodeInInput() {
        List<PosDataItemDto> items = new ArrayList<>();

        PosDataItemDto item1 = new PosDataItemDto();
        item1.setItemCode("01");
        item1.setItemPrnt("0");
        item1.setItemName("Category 1");
        item1.setItemType(1);
        items.add(item1);

        PosDataItemDto item2 = new PosDataItemDto();
        item2.setItemCode("01"); // Duplicate!
        item2.setItemPrnt("0");
        item2.setItemName("Category 2");
        item2.setItemType(1);
        items.add(item2);

        PosMigrationException ex = assertThrows(PosMigrationException.class, () -> {
            posMigrationService.importPosData(items);
        });

        assertEquals(1, ex.getErrors().size());
        assertEquals("01", ex.getErrors().get(0).getItemCode());
        assertTrue(ex.getErrors().get(0).getReason().contains("Duplicate ItemCode"));
    }

    @Test
    public void testMissingParent() {
        List<PosDataItemDto> items = new ArrayList<>();

        PosDataItemDto item = new PosDataItemDto();
        item.setItemCode("0101");
        item.setItemPrnt("01"); // Parent "01" does not exist in input or DB!
        item.setItemName("Brand 1");
        item.setItemType(1);
        items.add(item);

        when(categoryRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(brandRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(productGroupRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(productRepository.findAllProductBarcodes()).thenReturn(Collections.emptySet());
        when(productBarcodeRepository.findAllBarcodeBarcodes()).thenReturn(Collections.emptySet());

        PosMigrationException ex = assertThrows(PosMigrationException.class, () -> {
            posMigrationService.importPosData(items);
        });

        assertEquals(1, ex.getErrors().size());
        assertEquals("0101", ex.getErrors().get(0).getItemCode());
        assertTrue(ex.getErrors().get(0).getReason().contains("Parent with code '01' was not found"));
    }

    @Test
    public void testInvalidItemType() {
        List<PosDataItemDto> items = new ArrayList<>();

        PosDataItemDto item = new PosDataItemDto();
        item.setItemCode("01");
        item.setItemPrnt("0");
        item.setItemName("Category 1");
        item.setItemType(3); // Invalid type! (only 1 or 2 allowed)
        items.add(item);

        PosMigrationException ex = assertThrows(PosMigrationException.class, () -> {
            posMigrationService.importPosData(items);
        });

        assertEquals(1, ex.getErrors().size());
        assertEquals("01", ex.getErrors().get(0).getItemCode());
        assertTrue(ex.getErrors().get(0).getReason().contains("Invalid ItemType"));
    }

    @Test
    public void testExistingDatabaseDuplicate() {
        List<PosDataItemDto> items = new ArrayList<>();

        PosDataItemDto item = new PosDataItemDto();
        item.setItemCode("01");
        item.setItemPrnt("0");
        item.setItemName("Category 1");
        item.setItemType(1);
        items.add(item);

        // Mock that Category code "01" already exists in DB
        when(categoryRepository.findAllCodes()).thenReturn(Set.of("01"));
        when(brandRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(productGroupRepository.findAllCodes()).thenReturn(Collections.emptySet());
        when(productRepository.findAllProductBarcodes()).thenReturn(Collections.emptySet());
        when(productBarcodeRepository.findAllBarcodeBarcodes()).thenReturn(Collections.emptySet());

        PosMigrationException ex = assertThrows(PosMigrationException.class, () -> {
            posMigrationService.importPosData(items);
        });

        assertEquals(1, ex.getErrors().size());
        assertEquals("01", ex.getErrors().get(0).getItemCode());
        assertTrue(ex.getErrors().get(0).getReason().contains("already exists in database"));
    }
}
