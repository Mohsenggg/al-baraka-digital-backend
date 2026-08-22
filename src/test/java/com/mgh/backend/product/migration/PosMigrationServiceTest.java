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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PosMigrationServiceTest {

    @Mock private ProductCategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private ProductGroupRepository productGroupRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductBarcodeRepository productBarcodeRepository;
    @Mock private PosMigrationPersister posMigrationPersister;

    @InjectMocks
    private PosMigrationServiceImpl posMigrationService;

    // ── Shared setup ────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // Persister returns the entity it received (simulates save + assign ID)
        lenient().when(posMigrationPersister.saveCategory(any()))
                 .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveBrand(any()))
                 .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveProductGroup(any()))
                 .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveProduct(any(), any()))
                 .thenAnswer(inv -> inv.getArgument(0));
    }

    /** Stub the bulk-preload calls to return empty (nothing exists in DB yet). */
    private void stubEmptyDatabase() {
        lenient().when(categoryRepository.findAllWithCode()).thenReturn(Collections.emptyList());
        lenient().when(brandRepository.findAllWithCategory()).thenReturn(Collections.emptyList());
        lenient().when(productGroupRepository.findAllWithBrand()).thenReturn(Collections.emptyList());
        lenient().when(productRepository.findAllBarcodeAndNamePairs()).thenReturn(Collections.emptyList());
    }

    // ── Helper builders ─────────────────────────────────────────────────────────

    private PosDataItemDto item(String code, String prnt, String name, int type) {
        PosDataItemDto dto = new PosDataItemDto();
        dto.setItemCode(code);
        dto.setItemPrnt(prnt);
        dto.setItemName(name);
        dto.setItemType(type);
        return dto;
    }

    private PosDataItemDto product(String code, String prnt, String name,
                                   BigDecimal buying, BigDecimal selling,
                                   double minStock, double maxStock) {
        PosDataItemDto dto = item(code, prnt, name, 2);
        dto.setItemPrice0(buying);
        dto.setItemPrice1(selling);
        dto.setItemMinStock(minStock);
        dto.setItemMaxStock(maxStock);
        return dto;
    }

    // ── Tests ───────────────────────────────────────────────────────────────────

    @Test
    void testSuccessfulMigration() {
        stubEmptyDatabase();

        List<PosDataItemDto> items = List.of(
                item("01",             "0",     "مساحيق",           1),
                item("0101",           "01",    "شركة اريال",        1),
                item("01016",          "0101",  "اريال 1ك",          1),
                product("8006540852170", "01016", "اريال 1كيلو لافندر",
                        new BigDecimal("100.00"), new BigDecimal("120.00"), 5.0, 100.0)
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(4, result.getTotalRecords());
        assertEquals(4, result.getSuccessfulRecords());
        assertEquals(0, result.getAlreadyExisting());
        assertEquals(0, result.getFailedRecords());
        assertTrue(result.getFailures().isEmpty());

        // Verify Category
        ArgumentCaptor<ProductCategory> catCap = ArgumentCaptor.forClass(ProductCategory.class);
        verify(posMigrationPersister).saveCategory(catCap.capture());
        assertEquals("01", catCap.getValue().getCode());
        assertEquals("مساحيق", catCap.getValue().getName());

        // Verify Brand
        ArgumentCaptor<Brand> brandCap = ArgumentCaptor.forClass(Brand.class);
        verify(posMigrationPersister).saveBrand(brandCap.capture());
        assertEquals("0101", brandCap.getValue().getCode());
        assertEquals("01", brandCap.getValue().getCategory().getCode());

        // Verify ProductGroup
        ArgumentCaptor<ProductGroup> pgCap = ArgumentCaptor.forClass(ProductGroup.class);
        verify(posMigrationPersister).saveProductGroup(pgCap.capture());
        assertEquals("01016", pgCap.getValue().getCode());
        assertEquals("0101", pgCap.getValue().getBrand().getCode());

        // Verify Product + barcode
        ArgumentCaptor<Product>        prodCap = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<ProductBarcode> bcCap   = ArgumentCaptor.forClass(ProductBarcode.class);
        verify(posMigrationPersister).saveProduct(prodCap.capture(), bcCap.capture());
        Product prod = prodCap.getValue();
        assertEquals("8006540852170", prod.getBarcode());
        assertEquals("01016", prod.getProductGroup().getCode());
        assertEquals("01", prod.getCategory().getCode());
        assertEquals(5.0,   prod.getMinStockLevel());
        assertEquals(100.0, prod.getMaxStockLevel());
        ProductBarcode pb = bcCap.getValue();
        assertEquals(new BigDecimal("100.00"), pb.getBuyingPrice());
        assertEquals(new BigDecimal("120.00"), pb.getSellingPrice());
        assertTrue(pb.isDefault());
    }

    // ── alreadyExisting tests ────────────────────────────────────────────────────

    @Test
    void testCategoryAlreadyExisting_sameCodeAndName_isSkippedNotFailed() {
        // Pre-existing Category "01" with same name
        ProductCategory existingCat = ProductCategory.builder()
                .id(1L).code("01").name("مساحيق").build();
        when(categoryRepository.findAllWithCode()).thenReturn(List.of(existingCat));
        when(brandRepository.findAllWithCategory()).thenReturn(Collections.emptyList());
        when(productGroupRepository.findAllWithBrand()).thenReturn(Collections.emptyList());
        when(productRepository.findAllBarcodeAndNamePairs()).thenReturn(Collections.emptyList());

        MigrationResultDto result = posMigrationService.importPosData(
                List.of(item("01", "0", "مساحيق", 1))
        );

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalRecords());
        assertEquals(0, result.getSuccessfulRecords());
        assertEquals(1, result.getAlreadyExisting());
        assertEquals(0, result.getFailedRecords());
        verify(posMigrationPersister, never()).saveCategory(any());
    }

    @Test
    void testCategoryAlreadyExisting_sameCodeDifferentName_isConflict() {
        ProductCategory existingCat = ProductCategory.builder()
                .id(1L).code("01").name("مساحيق قديمة").build();
        when(categoryRepository.findAllWithCode()).thenReturn(List.of(existingCat));
        when(brandRepository.findAllWithCategory()).thenReturn(Collections.emptyList());
        when(productGroupRepository.findAllWithBrand()).thenReturn(Collections.emptyList());
        when(productRepository.findAllBarcodeAndNamePairs()).thenReturn(Collections.emptyList());

        MigrationResultDto result = posMigrationService.importPosData(
                List.of(item("01", "0", "مساحيق جديدة", 1))
        );

        assertTrue(result.isSuccess());
        assertEquals(1, result.getFailedRecords());
        assertEquals(0, result.getAlreadyExisting());
        MigrationErrorDto.ValidationError err = result.getFailures().get(0);
        assertEquals("01", err.getItemCode());
        assertTrue(err.getReason().contains("already exists with a different name"));
        verify(posMigrationPersister, never()).saveCategory(any());
    }

    @Test
    void testAlreadyExistingParent_childrenAreStillInserted() {
        // Category "01" already in DB — it should be skipped (alreadyExisting)
        // but Brands that belong to it should still be inserted successfully.
        ProductCategory existingCat = ProductCategory.builder()
                .id(1L).code("01").name("مساحيق").build();
        when(categoryRepository.findAllWithCode()).thenReturn(List.of(existingCat));
        when(brandRepository.findAllWithCategory()).thenReturn(Collections.emptyList());
        when(productGroupRepository.findAllWithBrand()).thenReturn(Collections.emptyList());
        when(productRepository.findAllBarcodeAndNamePairs()).thenReturn(Collections.emptyList());

        List<PosDataItemDto> items = List.of(
                item("01",   "0",  "مساحيق",     1),   // already existing
                item("0101", "01", "شركة اريال", 1)    // new — parent resolvable from preloaded map
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalRecords());
        assertEquals(1, result.getSuccessfulRecords()); // brand is new
        assertEquals(1, result.getAlreadyExisting());   // category skipped
        assertEquals(0, result.getFailedRecords());

        verify(posMigrationPersister, never()).saveCategory(any());
        verify(posMigrationPersister, times(1)).saveBrand(any());
    }

    @Test
    void testProductAlreadyExisting_sameBarCodeAndName_isSkipped() {
        stubEmptyDatabase();
        when(productRepository.findAllBarcodeAndNamePairs())
                .thenReturn(List.of(new Object[]{"8006540852170", "اريال 1كيلو لافندر"}));

        // Build minimal hierarchy in preloaded maps via stubs (Category "01" already in DB)
        ProductCategory cat = ProductCategory.builder().id(1L).code("01").name("مساحيق").build();
        Brand brand = Brand.builder().id(1L).code("0101").name("اريال").category(cat).build();
        ProductGroup pg = ProductGroup.builder().id(1L).code("01016").name("اريال 1ك").brand(brand).build();
        when(categoryRepository.findAllWithCode()).thenReturn(List.of(cat));
        when(brandRepository.findAllWithCategory()).thenReturn(List.of(brand));
        when(productGroupRepository.findAllWithBrand()).thenReturn(List.of(pg));

        MigrationResultDto result = posMigrationService.importPosData(
                List.of(product("8006540852170", "01016", "اريال 1كيلو لافندر",
                        new BigDecimal("100"), new BigDecimal("120"), 5, 100))
        );

        assertTrue(result.isSuccess());
        assertEquals(1, result.getAlreadyExisting());
        assertEquals(0, result.getSuccessfulRecords());
        verify(posMigrationPersister, never()).saveProduct(any(), any());
    }

    // ── Partial success / error-resilience tests ─────────────────────────────────

    @Test
    void testPartialSuccess_validTreePersistedWhileInvalidTreeFails() {
        stubEmptyDatabase();

        // Tree A: valid (Category "01" + Brand "0101") — should succeed
        // Tree B: invalid type on Category "02" — Category fails; Brand "0201" cascades
        List<PosDataItemDto> items = List.of(
                item("01",   "0",  "Category A",  1),
                item("0101", "01", "Brand A",      1),
                item("02",   "0",  "Category B",  2),  // type=2 for a root → invalid
                item("0201", "02", "Brand B",      1)
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(4, result.getTotalRecords());
        assertEquals(2, result.getSuccessfulRecords());
        assertEquals(2, result.getFailedRecords());
        assertEquals(0, result.getAlreadyExisting());

        verify(posMigrationPersister, times(1))
                .saveCategory(argThat(c -> c.getCode().equals("01")));
        verify(posMigrationPersister, times(1))
                .saveBrand(argThat(b -> b.getCode().equals("0101")));
        verify(posMigrationPersister, never())
                .saveCategory(argThat(c -> c.getCode().equals("02")));
        verify(posMigrationPersister, never())
                .saveBrand(argThat(b -> b.getCode().equals("0201")));
    }

    @Test
    void testArbitraryInputOrder_hierarchyResolvedCorrectly() {
        stubEmptyDatabase();

        // Items in reverse dependency order
        List<PosDataItemDto> items = List.of(
                product("8006540852170", "01016", "منتج", null, null, 0, 0),
                item("01016", "0101", "مجموعة", 1),
                item("0101",  "01",   "ماركة",  1),
                item("01",    "0",    "تصنيف",  1)
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(4, result.getSuccessfulRecords());
        assertEquals(0, result.getFailedRecords());
    }

    @Test
    void testDuplicateInputCode_firstOccurrenceSucceeds_secondFails() {
        stubEmptyDatabase();

        List<PosDataItemDto> items = List.of(
                item("01", "0", "Category 1", 1),
                item("01", "0", "Category 1 duplicate", 1)  // duplicate code in same payload
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalRecords());
        assertEquals(1, result.getSuccessfulRecords());
        assertEquals(1, result.getFailedRecords());
        assertTrue(result.getFailures().get(0).getReason().contains("Conflicting duplicate ItemCode"));
        verify(posMigrationPersister, times(1)).saveCategory(any());
    }

    @Test
    void testMissingParent_itemFailsGracefully() {
        stubEmptyDatabase();  // includes empty preloaded maps → "01" not in DB

        MigrationResultDto result = posMigrationService.importPosData(
                List.of(item("0101", "01", "Brand", 1))  // parent "01" nowhere
        );

        assertTrue(result.isSuccess());
        assertEquals(1, result.getFailedRecords());
        assertTrue(result.getFailures().get(0).getReason().contains("was not found in input or database"));
    }

    @Test
    void testInvalidItemType_itemFailsGracefully() {
        stubEmptyDatabase();

        MigrationResultDto result = posMigrationService.importPosData(
                List.of(item("01", "0", "Category", 3))  // type=3 is invalid
        );

        assertTrue(result.isSuccess());
        assertEquals(1, result.getFailedRecords());
        assertTrue(result.getFailures().get(0).getReason().contains("Invalid ItemType"));
    }
}
