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

    @BeforeEach
    void setUp() {
        lenient().when(posMigrationPersister.saveCategory(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveBrand(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveProductGroup(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveProduct(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubEmptyDatabase() {
        lenient().when(categoryRepository.findAllWithCode()).thenReturn(Collections.emptyList());
        lenient().when(brandRepository.findAllWithCategory()).thenReturn(Collections.emptyList());
        lenient().when(productGroupRepository.findAllWithBrand()).thenReturn(Collections.emptyList());
        lenient().when(productRepository.findAllBarcodeAndNamePairs()).thenReturn(Collections.emptyList());
    }

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

    // ── Variable Hierarchy Depth Tests ──────────────────────────────────────────

    @Test
    void testTwoLevelHierarchy_ProductDirectlyUnderProductGroupUnderCategory() {
        // Example: Category "04" -> ProductGroup "05015" -> Product "6224008563570"
        stubEmptyDatabase();

        List<PosDataItemDto> items = List.of(
                item("04", "0", "قسم المنظفات", 1),
                item("05015", "04", "مجموعة صابون سائل", 1),
                product("6224008563570", "05015", "صابون سائل لافندر",
                        new BigDecimal("25.00"), new BigDecimal("30.00"), 2.0, 50.0)
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getTotalRecords());
        assertEquals(3, result.getSuccessfulRecords());
        assertEquals(0, result.getAlreadyExisting());
        assertEquals(0, result.getFailedRecords());
        assertTrue(result.getFailures().isEmpty());

        // Verify Category
        ArgumentCaptor<ProductCategory> catCap = ArgumentCaptor.forClass(ProductCategory.class);
        verify(posMigrationPersister).saveCategory(catCap.capture());
        assertEquals("04", catCap.getValue().getCode());
        assertEquals("قسم المنظفات", catCap.getValue().getName());

        // Verify ProductGroup
        ArgumentCaptor<ProductGroup> pgCap = ArgumentCaptor.forClass(ProductGroup.class);
        verify(posMigrationPersister).saveProductGroup(pgCap.capture());
        ProductGroup savedPg = pgCap.getValue();
        assertEquals("05015", savedPg.getCode());
        assertEquals("مجموعة صابون سائل", savedPg.getName());
        assertEquals("04", savedPg.getCategory().getCode());
        assertNull(savedPg.getBrand());

        // Verify Product
        ArgumentCaptor<Product> prodCap = ArgumentCaptor.forClass(Product.class);
        verify(posMigrationPersister).saveProduct(prodCap.capture(), any());
        Product savedProd = prodCap.getValue();
        assertEquals("6224008563570", savedProd.getBarcode());
        assertEquals("05015", savedProd.getProductGroup().getCode());
        assertEquals("04", savedProd.getCategory().getCode());
    }

    @Test
    void testThreeLevelHierarchy_Category_Brand_ProductGroup_Product() {
        // Example: Category "01" -> Brand "0101" -> ProductGroup "01016" -> Product "8006540852170"
        stubEmptyDatabase();

        List<PosDataItemDto> items = List.of(
                item("01", "0", "مساحيق", 1),
                item("0101", "01", "شركة اريال", 1),
                item("01016", "0101", "اريال 1ك", 1),
                product("8006540852170", "01016", "اريال 1كيلو لافندر",
                        new BigDecimal("100.00"), new BigDecimal("120.00"), 5.0, 100.0)
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(4, result.getTotalRecords());
        assertEquals(4, result.getSuccessfulRecords());
        assertEquals(0, result.getFailedRecords());

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

        // Verify Product
        ArgumentCaptor<Product> prodCap = ArgumentCaptor.forClass(Product.class);
        verify(posMigrationPersister).saveProduct(prodCap.capture(), any());
        assertEquals("8006540852170", prodCap.getValue().getBarcode());
        assertEquals("01016", prodCap.getValue().getProductGroup().getCode());
        assertEquals("01", prodCap.getValue().getCategory().getCode());
    }

    @Test
    void testMixedHierarchyDepthsInSinglePayload() {
        // Mixed:
        // Tree 1: Category "02" -> ProductGroup "0202" -> Product "6225000223844" (depth 2)
        // Tree 2: Category "01" -> Brand "0101" -> ProductGroup "01016" -> Product "8006540852170" (depth 3)
        stubEmptyDatabase();

        List<PosDataItemDto> items = List.of(
                item("02", "0", "عناية شخصية", 1),
                item("0202", "02", "شامبو", 1),
                product("6225000223844", "0202", "شامبو 400مل", new BigDecimal("40"), new BigDecimal("50"), 1, 20),
                item("01", "0", "مساحيق", 1),
                item("0101", "01", "شركة اريال", 1),
                item("01016", "0101", "اريال 1ك", 1),
                product("8006540852170", "01016", "اريال 1كيلو لافندر", new BigDecimal("100"), new BigDecimal("120"), 5, 100)
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(7, result.getTotalRecords());
        assertEquals(7, result.getSuccessfulRecords());
        assertEquals(0, result.getFailedRecords());
    }

    // ── Existing Record & Conflict Detection Tests ──────────────────────────────

    @Test
    void testAlreadyExisting_PreloadedEntitiesSkipped() {
        ProductCategory existingCat = ProductCategory.builder().id(1L).code("04").name("قسم المنظفات").build();
        ProductGroup existingPg = ProductGroup.builder().id(1L).code("05015").name("مجموعة صابون سائل").category(existingCat).build();

        when(categoryRepository.findAllWithCode()).thenReturn(List.of(existingCat));
        when(brandRepository.findAllWithCategory()).thenReturn(Collections.emptyList());
        when(productGroupRepository.findAllWithBrand()).thenReturn(List.of(existingPg));
        when(productRepository.findAllBarcodeAndNamePairs())
                .thenReturn(Collections.singletonList(new Object[]{"6224008563570", "صابون سائل لافندر"}));

        List<PosDataItemDto> items = List.of(
                item("04", "0", "قسم المنظفات", 1),
                item("05015", "04", "مجموعة صابون سائل", 1),
                product("6224008563570", "05015", "صابون سائل لافندر", new BigDecimal("25"), new BigDecimal("30"), 2, 50)
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getTotalRecords());
        assertEquals(0, result.getSuccessfulRecords());
        assertEquals(3, result.getAlreadyExisting());
        assertEquals(0, result.getFailedRecords());

        verify(posMigrationPersister, never()).saveCategory(any());
        verify(posMigrationPersister, never()).saveProductGroup(any());
        verify(posMigrationPersister, never()).saveProduct(any(), any());
    }

    @Test
    void testConflictDetection_SameCodeDifferentName() {
        ProductCategory existingCat = ProductCategory.builder().id(1L).code("04").name("اسم قديم").build();

        when(categoryRepository.findAllWithCode()).thenReturn(List.of(existingCat));
        when(brandRepository.findAllWithCategory()).thenReturn(Collections.emptyList());
        when(productGroupRepository.findAllWithBrand()).thenReturn(Collections.emptyList());
        when(productRepository.findAllBarcodeAndNamePairs()).thenReturn(Collections.emptyList());

        List<PosDataItemDto> items = List.of(
                item("04", "0", "اسم جديد", 1),
                item("05015", "04", "مجموعة", 1),
                product("6224008563570", "05015", "منتج", new BigDecimal("25"), new BigDecimal("30"), 2, 50)
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getTotalRecords());
        assertEquals(0, result.getSuccessfulRecords());
        assertEquals(3, result.getFailedRecords());
        assertEquals(0, result.getAlreadyExisting());

        assertTrue(result.getFailures().get(0).getReason().contains("already exists with a different name"));
    }

    @Test
    void testArbitraryInputOrder_HierarchyResolvedCorrectly() {
        stubEmptyDatabase();

        // Items in reverse dependency order
        List<PosDataItemDto> items = List.of(
                product("6224008563570", "05015", "منتج", null, null, 0, 0),
                item("05015", "04", "مجموعة", 1),
                item("04", "0", "تصنيف", 1)
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getSuccessfulRecords());
        assertEquals(0, result.getFailedRecords());
    }

    @Test
    void testMissingParent_ItemFailsGracefully() {
        stubEmptyDatabase();

        MigrationResultDto result = posMigrationService.importPosData(
                List.of(product("6224008563570", "9999", "منتج بدون أب", null, null, 0, 0))
        );

        assertTrue(result.isSuccess());
        assertEquals(1, result.getFailedRecords());
        assertTrue(result.getFailures().get(0).getReason().contains("was not found in input or database"));
    }
}
