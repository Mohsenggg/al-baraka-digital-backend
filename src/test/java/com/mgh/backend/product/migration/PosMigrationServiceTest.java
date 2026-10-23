package com.mgh.backend.product.migration;

import com.mgh.backend.product.entity.Brand;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.ProductGroup;
import com.mgh.backend.product.repository.BrandRepository;
import com.mgh.backend.product.repository.CatalogProvenanceRepository;
import com.mgh.backend.product.repository.ProductCategoryRepository;
import com.mgh.backend.product.repository.ProductGroupRepository;
import com.mgh.backend.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Mock private CatalogProvenanceRepository provenanceRepository;
    @Mock private PosMigrationPersister posMigrationPersister;

    @InjectMocks
    private PosMigrationServiceImpl posMigrationService;

    @BeforeEach
    void setUp() {
        lenient().when(posMigrationPersister.saveCategories(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveBrands(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveProductGroups(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveProducts(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(posMigrationPersister.saveAllProvenance(any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubEmptyDatabase() {
        lenient().when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(brandRepository.findAllWithCategory()).thenReturn(Collections.emptyList());
        lenient().when(productGroupRepository.findAllWithBrand()).thenReturn(Collections.emptyList());
        lenient().when(productRepository.findAllBarcodeAndNamePairs()).thenReturn(Collections.emptyList());
        lenient().when(provenanceRepository.findAllExistingCatalogAndSourceIdPairs()).thenReturn(Collections.emptyList());
    }

    private PosDataItemDto item(Long id, String code, String prnt, String name, int type) {
        PosDataItemDto dto = new PosDataItemDto();
        dto.setId(id);
        dto.setItemCode(code);
        dto.setItemPrnt(prnt);
        dto.setItemName(name);
        dto.setItemType(type);
        return dto;
    }

    private PosDataItemDto product(Long id, String code, String prnt, String name,
                                   BigDecimal buying, BigDecimal selling) {
        PosDataItemDto dto = item(id, code, prnt, name, 2);
        dto.setItemPrice0(buying);
        dto.setItemPrice1(selling);
        return dto;
    }

    @Test
    void testCanonicalCategoryRemapping_Legacy03MapsToCanonical05() {
        stubEmptyDatabase();

        // Part 1 Legacy item under 03 ("خامات")
        List<PosDataItemDto> items = List.of(
                item(28699L, "03", "0", "خامات", 1),
                item(28545L, "0306", "03", "فريدال", 1),
                product(28546L, "030601", "0306", "كيلو رائحة توت", new BigDecimal("40"), new BigDecimal("50"))
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getTotalRecords());
        assertEquals(2, result.getCreatedRecords()); // Category 10/canonical + product group + product
        assertEquals(1, result.getMergedRecords());  // Legacy category 03 merged into canonical 05
        assertEquals(0, result.getFailedRecords());
    }

    @Test
    void testLegacyBarcodeDisambiguation_CollidingCodeGetsLegPrefix() {
        stubEmptyDatabase();

        // Modern Sulfonic acid (ID 36401, code 050201) vs Legacy Sunsilk shampoo (ID 28712, code 050201)
        List<PosDataItemDto> items = List.of(
                // Modern base
                item(36282L, "05", "0", "خامات", 1),
                item(36400L, "0502", "05", "خمات تصنيع", 1),
                product(36401L, "050201", "0502", "كيلو سلفونك عادي", new BigDecimal("70"), new BigDecimal("80")),

                // Legacy item sharing same code
                item(29060L, "05", "0", "مستحضرات تجميل", 1),
                item(29276L, "0502", "05", "ليفر", 1),
                product(28712L, "050201", "0502", "شريط شامبو صن سيلك اكياس7مل", new BigDecimal("10"), new BigDecimal("14"))
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(6, result.getTotalRecords());
        assertEquals(5, result.getCreatedRecords()); // 3 modern + 2 legacy (disambiguated product is a subset of created)
        assertEquals(1, result.getDisambiguatedRecords()); // 1 disambiguated legacy product (LEG-050201)
        assertEquals(1, result.getMergedRecords()); // Legacy category 05 merged into canonical 04
        assertEquals(0, result.getFailedRecords());
    }

    @Test
    void testIntraModernPriceRevision_LowerIdMergedIntoHigherId() {
        stubEmptyDatabase();

        // 6223006126022 Oxi sachet: ID 37524 at 1.5 EGP, ID 38114 at 2.0 EGP
        List<PosDataItemDto> items = List.of(
                item(36278L, "01", "0", "مساحيق", 1),
                item(36288L, "0102", "01", "شركة اوكسي", 1),
                product(37524L, "6223006126022", "0102", "اوكسي سائل 1.5", new BigDecimal("1.33"), new BigDecimal("1.50")),
                product(38114L, "6223006126022", "0102", "اوكسي سائل 2ج", new BigDecimal("1.60"), new BigDecimal("2.00"))
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(4, result.getTotalRecords());
        assertEquals(1, result.getMergedRecords()); // Lower ID 37524 merged
        assertEquals(0, result.getFailedRecords());
    }

    @Test
    void testIntraModernDuplicateCollision_AvonaIsolatedWithoutFailingBatch() {
        stubEmptyDatabase();

        // Barcode 305210231597: ID 37256 (Unilever) vs ID 37597 (Avona)
        List<PosDataItemDto> items = List.of(
                item(36281L, "04", "0", "مستحضرات تجميل", 1),
                item(36376L, "040", "04", "ليفر", 1),
                product(37256L, "305210231597", "040", "فيزلين ثيربي شفايف", new BigDecimal("7.5"), new BigDecimal("12.0")),
                product(37597L, "305210231597", "040", "فازلين افونا", new BigDecimal("7.9"), new BigDecimal("12.0"))
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess()); // Entire batch is successful
        assertEquals(4, result.getTotalRecords());
        assertEquals(1, result.getFailedRecords()); // Avona is isolated as unresolved conflict
        assertEquals(1, result.getFailures().size());
        assertTrue(result.getFailures().get(0).getReason().contains("Avona"));
        assertTrue(result.getFailures().get(0).getReason().contains("305210231597"));
    }

    @Test
    void testIdempotency_SecondRunCreatesZeroDuplicates() {
        ProductCategory cat = ProductCategory.builder().id(1L).code("01").name("مساحيق").build();
        Brand brand = Brand.builder().id(1L).code("0101").name("شركة اريال").category(cat).build();

        when(categoryRepository.findAll()).thenReturn(List.of(cat));
        when(brandRepository.findAllWithCategory()).thenReturn(List.of(brand));
        when(productGroupRepository.findAllWithBrand()).thenReturn(Collections.emptyList());
        List<Object[]> existingProducts = new ArrayList<>();
        existingProducts.add(new Object[]{"8006540852170", "اريال 1كيلو"});
        when(productRepository.findAllBarcodeAndNamePairs()).thenReturn(existingProducts);

        List<Object[]> existingProv = new ArrayList<>();
        existingProv.add(new Object[]{"MODERN", 36278L});
        existingProv.add(new Object[]{"MODERN", 36287L});
        existingProv.add(new Object[]{"MODERN", 36398L});
        when(provenanceRepository.findAllExistingCatalogAndSourceIdPairs()).thenReturn(existingProv);

        List<PosDataItemDto> items = List.of(
                item(36278L, "01", "0", "مساحيق", 1),
                item(36287L, "0101", "01", "شركة اريال", 1),
                product(36398L, "8006540852170", "0101", "اريال 1كيلو", new BigDecimal("50"), new BigDecimal("60"))
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getTotalRecords());
        assertEquals(0, result.getCreatedRecords()); // Zero created on second run!
        assertEquals(3, result.getAlreadyExisting());
        assertEquals(0, result.getFailedRecords());

        verify(posMigrationPersister, never()).saveProducts(any());
        verify(posMigrationPersister, never()).saveAllProvenance(any());
    }

    @Test
    void testIdempotency_SecondRunPreservesMergedCountWithoutDuplicateProvenanceOrPriceUpdates() {
        ProductCategory cat01 = ProductCategory.builder().id(1L).code("01").name("مساحيق").build();
        ProductCategory cat05 = ProductCategory.builder().id(5L).code("05").name("خامات").build();
        Brand brandAriel = Brand.builder().id(10L).code("0101").name("شركة اريال").category(cat01).build();

        when(categoryRepository.findAll()).thenReturn(List.of(cat01, cat05));
        when(brandRepository.findAllWithCategory()).thenReturn(List.of(brandAriel));
        when(productGroupRepository.findAllWithBrand()).thenReturn(Collections.emptyList());

        List<Object[]> existingProducts = new ArrayList<>();
        existingProducts.add(new Object[]{"6223006126022", "اوكسي سائل 2ج"});
        existingProducts.add(new Object[]{"050230", "كيلو لون كلور"});
        when(productRepository.findAllBarcodeAndNamePairs()).thenReturn(existingProducts);

        // Preload provenance from Run 1
        List<Object[]> existingProv = new ArrayList<>();
        existingProv.add(new Object[]{"LEGACY", 28699L}); // Legacy root 03
        existingProv.add(new Object[]{"LEGACY", 28544L}); // Legacy brand 0101
        existingProv.add(new Object[]{"LEGACY", 28547L}); // Legacy product 030507
        existingProv.add(new Object[]{"MODERN", 37524L}); // Modern old price revision
        existingProv.add(new Object[]{"MODERN", 38114L}); // Modern active product
        when(provenanceRepository.findAllExistingCatalogAndSourceIdPairs()).thenReturn(existingProv);

        List<PosDataItemDto> items = List.of(
                item(28699L, "03", "0", "خامات", 1),                                     // Legacy Root Category -> merged
                item(28544L, "0101", "01", "اريال", 1),                                  // Legacy Brand -> merged
                product(28547L, "030507", "0305", "كيلو لون كلور", new BigDecimal("10"), new BigDecimal("15")), // Legacy chemical -> merged
                product(37524L, "6223006126022", "0102", "اوكسي سائل 1.5", new BigDecimal("1.33"), new BigDecimal("1.50")), // Lower ID -> merged
                product(38114L, "6223006126022", "0102", "اوكسي سائل 2ج", new BigDecimal("1.60"), new BigDecimal("2.00"))   // Active -> already existing
        );

        MigrationResultDto result = posMigrationService.importPosData(items);

        assertTrue(result.isSuccess());
        assertEquals(5, result.getTotalRecords());
        assertEquals(0, result.getCreatedRecords());     // 0 created on second run
        assertEquals(4, result.getMergedRecords());      // 4 merges recognized idempotently!
        assertEquals(1, result.getAlreadyExisting());    // 1 active product already existing
        assertEquals(0, result.getFailedRecords());

        // Zero duplicate products or provenance saved!
        verify(posMigrationPersister, never()).saveProducts(any());
        verify(posMigrationPersister, never()).saveAllProvenance(any());
    }
}
