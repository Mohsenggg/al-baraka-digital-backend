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
import java.util.List;
import java.util.Optional;
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
    @Mock
    private PosMigrationPersister posMigrationPersister;

    @InjectMocks
    private PosMigrationServiceImpl posMigrationService;

    @BeforeEach
    public void setUp() {
        // Setup default behavior for posMigrationPersister to return input entities
        lenient().when(posMigrationPersister.saveCategory(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveBrand(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveProductGroup(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveProduct(any(), any())).thenAnswer(inv -> inv.getArgument(0));
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
        when(categoryRepository.existsByCode(any())).thenReturn(false);
        when(brandRepository.existsByCode(any())).thenReturn(false);
        when(productGroupRepository.existsByCode(any())).thenReturn(false);
        when(productRepository.existsByBarcodeAndDeletedAtIsNull(any())).thenReturn(false);
        when(productBarcodeRepository.existsByBarcodeAndDeletedAtIsNull(any())).thenReturn(false);

        // Execute migration
        MigrationResultDto result = posMigrationService.importPosData(items);

        // Verify result DTO
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(4, result.getTotalRecords());
        assertEquals(4, result.getSuccessfulRecords());
        assertEquals(0, result.getFailedRecords());
        assertTrue(result.getFailures().isEmpty());

        // Verify saved structures via Persister
        ArgumentCaptor<ProductCategory> catCaptor = ArgumentCaptor.forClass(ProductCategory.class);
        verify(posMigrationPersister).saveCategory(catCaptor.capture());
        ProductCategory savedCat = catCaptor.getValue();
        assertEquals("01", savedCat.getCode());
        assertEquals("مساحيق", savedCat.getName());

        ArgumentCaptor<Brand> brandCaptor = ArgumentCaptor.forClass(Brand.class);
        verify(posMigrationPersister).saveBrand(brandCaptor.capture());
        Brand savedBrand = brandCaptor.getValue();
        assertEquals("0101", savedBrand.getCode());
        assertEquals("01", savedBrand.getCategory().getCode());

        ArgumentCaptor<ProductGroup> pgCaptor = ArgumentCaptor.forClass(ProductGroup.class);
        verify(posMigrationPersister).saveProductGroup(pgCaptor.capture());
        ProductGroup savedPg = pgCaptor.getValue();
        assertEquals("01016", savedPg.getCode());
        assertEquals("0101", savedPg.getBrand().getCode());

        ArgumentCaptor<Product> prodCaptor = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<ProductBarcode> bcCaptor = ArgumentCaptor.forClass(ProductBarcode.class);
        verify(posMigrationPersister).saveProduct(prodCaptor.capture(), bcCaptor.capture());
        Product prod = prodCaptor.getValue();
        ProductBarcode pb = bcCaptor.getValue();

        assertEquals("8006540852170", prod.getBarcode());
        assertEquals("اريال 1كيلو لافندر", prod.getName());
        assertEquals("01016", prod.getProductGroup().getCode());
        assertEquals("01", prod.getCategory().getCode()); // category link verified!
        assertEquals(5.0, prod.getMinStockLevel());
        assertEquals(100.0, prod.getMaxStockLevel());

        // Verify default barcode
        assertEquals("8006540852170", pb.getBarcode());
        assertEquals(new BigDecimal("100.00"), pb.getBuyingPrice());
        assertEquals(new BigDecimal("120.00"), pb.getSellingPrice());
        assertTrue(pb.isDefault());
    }

    @Test
    public void testPartialSuccessMigration() {
        // Mixed input: 
        // - Tree A (valid Category '01' and Brand '0101') -> should succeed
        // - Tree B (invalid: Category '02' has invalid ItemType = 2) -> Category should fail, child Brand '0201' should fail
        List<PosDataItemDto> items = new ArrayList<>();

        // Tree A
        PosDataItemDto catA = new PosDataItemDto();
        catA.setItemCode("01");
        catA.setItemPrnt("0");
        catA.setItemName("Category A");
        catA.setItemType(1);
        items.add(catA);

        PosDataItemDto brandA = new PosDataItemDto();
        brandA.setItemCode("0101");
        brandA.setItemPrnt("01");
        brandA.setItemName("Brand A");
        brandA.setItemType(1);
        items.add(brandA);

        // Tree B
        PosDataItemDto catB = new PosDataItemDto();
        catB.setItemCode("02");
        catB.setItemPrnt("0");
        catB.setItemName("Category B");
        catB.setItemType(2); // Invalid type for Category (hops=0)
        items.add(catB);

        PosDataItemDto brandB = new PosDataItemDto();
        brandB.setItemCode("0201");
        brandB.setItemPrnt("02");
        brandB.setItemName("Brand B");
        brandB.setItemType(1);
        items.add(brandB);

        when(categoryRepository.existsByCode(any())).thenReturn(false);
        when(brandRepository.existsByCode(any())).thenReturn(false);

        // Execute migration
        MigrationResultDto result = posMigrationService.importPosData(items);

        // Verify result
        assertNotNull(result);
        assertTrue(result.isSuccess()); // Process completes successfully
        assertEquals(4, result.getTotalRecords());
        assertEquals(2, result.getSuccessfulRecords()); // Tree A (catA and brandA)
        assertEquals(2, result.getFailedRecords()); // Tree B (catB and brandB)

        // Verify Tree A was saved
        verify(posMigrationPersister, times(1)).saveCategory(argThat(c -> c.getCode().equals("01")));
        verify(posMigrationPersister, times(1)).saveBrand(argThat(b -> b.getCode().equals("0101")));

        // Verify Tree B was NOT saved
        verify(posMigrationPersister, never()).saveCategory(argThat(c -> c.getCode().equals("02")));
        verify(posMigrationPersister, never()).saveBrand(argThat(b -> b.getCode().equals("0201")));

        // Verify failures messages
        assertEquals(2, result.getFailures().size());
        assertTrue(result.getFailures().stream().anyMatch(f -> f.getItemCode().equals("02") && f.getReason().contains("must have ItemType = 1")));
        assertTrue(result.getFailures().stream().anyMatch(f -> f.getItemCode().equals("0201") && f.getReason().contains("failed validation/import")));
    }

    @Test
    public void testMigrationWithArbitraryOrder() {
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

        when(categoryRepository.existsByCode(any())).thenReturn(false);
        when(brandRepository.existsByCode(any())).thenReturn(false);
        when(productGroupRepository.existsByCode(any())).thenReturn(false);
        when(productRepository.existsByBarcodeAndDeletedAtIsNull(any())).thenReturn(false);
        when(productBarcodeRepository.existsByBarcodeAndDeletedAtIsNull(any())).thenReturn(false);

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(4, result.getSuccessfulRecords());
        assertEquals(0, result.getFailedRecords());
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

        when(categoryRepository.existsByCode("01")).thenReturn(false);

        MigrationResultDto result = posMigrationService.importPosData(items);

        // Verification - should complete successfully but mark one as duplicate failure, and save the other!
        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalRecords());
        assertEquals(1, result.getSuccessfulRecords());
        assertEquals(1, result.getFailedRecords());

        // Verify valid one was saved
        verify(posMigrationPersister, times(1)).saveCategory(any());

        assertEquals("01", result.getFailures().get(0).getItemCode());
        assertTrue(result.getFailures().get(0).getReason().contains("Conflicting duplicate ItemCode"));
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

        // Mock that parent code does not exist in the database
        when(categoryRepository.existsByCode("01")).thenReturn(false);
        when(brandRepository.existsByCode("01")).thenReturn(false);
        when(productGroupRepository.existsByCode("01")).thenReturn(false);

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getFailedRecords());
        assertEquals("0101", result.getFailures().get(0).getItemCode());
        assertTrue(result.getFailures().get(0).getReason().contains("was not found in input or database"));
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
        when(categoryRepository.existsByCode("01")).thenReturn(true);

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getFailedRecords());
        assertEquals("01", result.getFailures().get(0).getItemCode());
        assertTrue(result.getFailures().get(0).getReason().contains("already exists in database"));
    }
}
