package com.mgh.backend.product.service;

import com.mgh.backend.cashier.exception.BadRequestException;
import com.mgh.backend.cashier.exception.ConflictException;
import com.mgh.backend.cashier.exception.ResourceNotFoundException;
import com.mgh.backend.product.dto.response.BulkMoveProductsResponse;
import com.mgh.backend.product.entity.Brand;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.ProductGroup;
import com.mgh.backend.product.repository.BrandRepository;
import com.mgh.backend.product.repository.ProductCategoryRepository;
import com.mgh.backend.product.repository.ProductGroupRepository;
import com.mgh.backend.product.repository.ProductRepository;
import com.mgh.backend.product.service.impl.ProductTreeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductTreeServiceTest {

    @Mock
    private ProductCategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductGroupRepository productGroupRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private com.mgh.backend.product.mapper.ProductMapper productMapper;

    @InjectMocks
    private ProductTreeServiceImpl productTreeService;

    private ProductCategory cat1;
    private ProductCategory cat2;
    private Brand brand1;
    private ProductGroup group1;
    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        cat1 = ProductCategory.builder().id(1L).code("CAT1").name("Category 1").build();
        cat2 = ProductCategory.builder().id(2L).code("CAT2").name("Category 2").build();
        brand1 = Brand.builder().id(10L).code("BR1").name("Brand 1").category(cat1).build();
        group1 = ProductGroup.builder().id(100L).code("PG1").name("Group 1").brand(brand1).category(cat1).build();
        product1 = Product.builder().id(1001L).barcode("BC1").name("Product 1").productGroup(group1).category(cat1).build();
        product2 = Product.builder().id(1002L).barcode("BC2").name("Product 2").productGroup(group1).category(cat1).build();
    }

    // ==========================================
    // TASK 1: Node Deletion Tests
    // ==========================================
    @Nested
    @DisplayName("Task 1: Safe Node Deletion")
    class NodeDeletionTests {

        @Test
        @DisplayName("deleteCategory: Throws 404 if category does not exist")
        void deleteCategory_NotFound() {
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> productTreeService.deleteCategory(99L));
            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("deleteCategory: Throws 409 if category has brands")
        void deleteCategory_HasBrands() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat1));
            when(brandRepository.existsByCategoryId(1L)).thenReturn(true);

            assertThrows(ConflictException.class, () -> productTreeService.deleteCategory(1L));
            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("deleteCategory: Throws 409 if category has direct product groups")
        void deleteCategory_HasGroups() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat1));
            when(brandRepository.existsByCategoryId(1L)).thenReturn(false);
            when(productGroupRepository.existsByCategoryId(1L)).thenReturn(true);

            assertThrows(ConflictException.class, () -> productTreeService.deleteCategory(1L));
            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("deleteCategory: Throws 409 if category has products")
        void deleteCategory_HasProducts() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat1));
            when(brandRepository.existsByCategoryId(1L)).thenReturn(false);
            when(productGroupRepository.existsByCategoryId(1L)).thenReturn(false);
            when(productRepository.existsByCategoryIdAndDeletedAtIsNull(1L)).thenReturn(true);

            assertThrows(ConflictException.class, () -> productTreeService.deleteCategory(1L));
            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("deleteCategory: Deletes successfully when completely empty")
        void deleteCategory_Success() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat1));
            when(brandRepository.existsByCategoryId(1L)).thenReturn(false);
            when(productGroupRepository.existsByCategoryId(1L)).thenReturn(false);
            when(productRepository.existsByCategoryIdAndDeletedAtIsNull(1L)).thenReturn(false);

            productTreeService.deleteCategory(1L);

            verify(categoryRepository, times(1)).delete(cat1);
        }

        @Test
        @DisplayName("deleteBrand: Throws 409 if brand has product groups")
        void deleteBrand_HasGroups() {
            when(brandRepository.findById(10L)).thenReturn(Optional.of(brand1));
            when(productGroupRepository.existsByBrandId(10L)).thenReturn(true);

            assertThrows(ConflictException.class, () -> productTreeService.deleteBrand(10L));
            verify(brandRepository, never()).delete(any());
        }

        @Test
        @DisplayName("deleteBrand: Deletes successfully when empty")
        void deleteBrand_Success() {
            when(brandRepository.findById(10L)).thenReturn(Optional.of(brand1));
            when(productGroupRepository.existsByBrandId(10L)).thenReturn(false);

            productTreeService.deleteBrand(10L);

            verify(brandRepository, times(1)).delete(brand1);
        }

        @Test
        @DisplayName("deleteProductGroup: Throws 409 if group has products")
        void deleteProductGroup_HasProducts() {
            when(productGroupRepository.findById(100L)).thenReturn(Optional.of(group1));
            when(productRepository.existsByProductGroupIdAndDeletedAtIsNull(100L)).thenReturn(true);

            assertThrows(ConflictException.class, () -> productTreeService.deleteProductGroup(100L));
            verify(productGroupRepository, never()).delete(any());
        }

        @Test
        @DisplayName("deleteProductGroup: Deletes successfully when empty")
        void deleteProductGroup_Success() {
            when(productGroupRepository.findById(100L)).thenReturn(Optional.of(group1));
            when(productRepository.existsByProductGroupIdAndDeletedAtIsNull(100L)).thenReturn(false);

            productTreeService.deleteProductGroup(100L);

            verify(productGroupRepository, times(1)).delete(group1);
        }
    }

    // ==========================================
    // TASK 2: Node Renaming Tests
    // ==========================================
    @Nested
    @DisplayName("Task 2: Node Renaming with Scoped Uniqueness")
    class NodeRenamingTests {

        @Test
        @DisplayName("renameCategory: Throws 400 for short/blank names")
        void renameCategory_InvalidLength() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat1));

            assertThrows(BadRequestException.class, () -> productTreeService.renameCategory(1L, " "));
            assertThrows(BadRequestException.class, () -> productTreeService.renameCategory(1L, "A"));
        }

        @Test
        @DisplayName("renameCategory: Throws 409 on duplicate name")
        void renameCategory_Duplicate() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat1));
            when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Existing Cat", 1L)).thenReturn(true);

            assertThrows(ConflictException.class, () -> productTreeService.renameCategory(1L, "Existing Cat"));
            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("renameCategory: Renames and trims name successfully")
        void renameCategory_Success() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat1));
            when(categoryRepository.existsByNameIgnoreCaseAndIdNot("New Name", 1L)).thenReturn(false);

            productTreeService.renameCategory(1L, "  New Name  ");

            assertEquals("New Name", cat1.getName());
            verify(categoryRepository, times(1)).save(cat1);
        }

        @Test
        @DisplayName("renameBrand: Throws 409 if duplicate brand in same category")
        void renameBrand_DuplicateInCategory() {
            when(brandRepository.findById(10L)).thenReturn(Optional.of(brand1));
            when(brandRepository.existsByNameIgnoreCaseAndCategoryIdAndIdNot("Duplicate Brand", 1L, 10L)).thenReturn(true);

            assertThrows(ConflictException.class, () -> productTreeService.renameBrand(10L, "Duplicate Brand"));
            verify(brandRepository, never()).save(any());
        }

        @Test
        @DisplayName("renameBrand: Renames successfully")
        void renameBrand_Success() {
            when(brandRepository.findById(10L)).thenReturn(Optional.of(brand1));
            when(brandRepository.existsByNameIgnoreCaseAndCategoryIdAndIdNot("New Brand", 1L, 10L)).thenReturn(false);

            productTreeService.renameBrand(10L, "New Brand");

            assertEquals("New Brand", brand1.getName());
            verify(brandRepository, times(1)).save(brand1);
        }

        @Test
        @DisplayName("renameProductGroup: Throws 409 if duplicate group in same brand")
        void renameProductGroup_DuplicateInBrand() {
            when(productGroupRepository.findById(100L)).thenReturn(Optional.of(group1));
            when(productGroupRepository.existsByNameIgnoreCaseAndBrandIdAndIdNot("Duplicate Group", 10L, 100L)).thenReturn(true);

            assertThrows(ConflictException.class, () -> productTreeService.renameProductGroup(100L, "Duplicate Group"));
            verify(productGroupRepository, never()).save(any());
        }

        @Test
        @DisplayName("renameProductGroup: Renames successfully")
        void renameProductGroup_Success() {
            when(productGroupRepository.findById(100L)).thenReturn(Optional.of(group1));
            when(productGroupRepository.existsByNameIgnoreCaseAndBrandIdAndIdNot("New Group", 10L, 100L)).thenReturn(false);

            productTreeService.renameProductGroup(100L, "New Group");

            assertEquals("New Group", group1.getName());
            verify(productGroupRepository, times(1)).save(group1);
        }
    }

    // ==========================================
    // TASK 3: Moving Nodes & Products Tests
    // ==========================================
    @Nested
    @DisplayName("Task 3: Moving Nodes & Bulk Move Products")
    class MoveOperationsTests {

        @Test
        @DisplayName("moveBrand: Throws 400 when target category is same as current")
        void moveBrand_SameCategory() {
            when(brandRepository.findById(10L)).thenReturn(Optional.of(brand1));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat1));

            assertThrows(BadRequestException.class, () -> productTreeService.moveBrand(10L, 1L));
        }

        @Test
        @DisplayName("moveBrand: Throws 409 if duplicate brand name in target category")
        void moveBrand_DuplicateInTarget() {
            when(brandRepository.findById(10L)).thenReturn(Optional.of(brand1));
            when(categoryRepository.findById(2L)).thenReturn(Optional.of(cat2));
            when(brandRepository.existsByNameIgnoreCaseAndCategoryIdAndIdNot(brand1.getName(), 2L, 10L)).thenReturn(true);

            assertThrows(ConflictException.class, () -> productTreeService.moveBrand(10L, 2L));
        }

        @Test
        @DisplayName("moveBrand: Successfully moves brand and cascades category to groups and products")
        void moveBrand_Success() {
            when(brandRepository.findById(10L)).thenReturn(Optional.of(brand1));
            when(categoryRepository.findById(2L)).thenReturn(Optional.of(cat2));
            when(brandRepository.existsByNameIgnoreCaseAndCategoryIdAndIdNot(brand1.getName(), 2L, 10L)).thenReturn(false);
            when(productGroupRepository.findByBrandId(10L)).thenReturn(List.of(group1));

            productTreeService.moveBrand(10L, 2L);

            assertEquals(cat2, brand1.getCategory());
            assertEquals(cat2, group1.getCategory());
            verify(brandRepository, times(1)).save(brand1);
            verify(productGroupRepository, times(1)).saveAll(anyList());
            verify(productRepository, times(1)).updateCategoryForProductGroups(cat2, List.of(100L));
        }

        @Test
        @DisplayName("moveProductGroup: Successfully moves group and updates products category")
        void moveProductGroup_Success() {
            Brand brand2 = Brand.builder().id(20L).code("BR2").name("Brand 2").category(cat2).build();

            when(productGroupRepository.findById(100L)).thenReturn(Optional.of(group1));
            when(brandRepository.findById(20L)).thenReturn(Optional.of(brand2));
            when(productGroupRepository.existsByNameIgnoreCaseAndBrandIdAndIdNot(group1.getName(), 20L, 100L)).thenReturn(false);

            productTreeService.moveProductGroup(100L, 20L);

            assertEquals(brand2, group1.getBrand());
            assertEquals(cat2, group1.getCategory());
            verify(productGroupRepository, times(1)).save(group1);
            verify(productRepository, times(1)).updateCategoryForProductGroup(cat2, 100L);
        }

        @Test
        @DisplayName("moveProduct: Successfully reassigns product group and category")
        void moveProduct_Success() {
            ProductGroup group2 = ProductGroup.builder().id(200L).code("PG2").name("Group 2").category(cat2).build();

            when(productRepository.findByIdAndDeletedAtIsNull(1001L)).thenReturn(Optional.of(product1));
            when(productGroupRepository.findById(200L)).thenReturn(Optional.of(group2));

            productTreeService.moveProduct(1001L, 200L);

            assertEquals(group2, product1.getProductGroup());
            assertEquals(cat2, product1.getCategory());
            verify(productRepository, times(1)).save(product1);
        }

        @Test
        @DisplayName("bulkMoveProducts: Throws 404 if any product ID is missing or deleted")
        void bulkMoveProducts_MissingProducts() {
            ProductGroup group2 = ProductGroup.builder().id(200L).code("PG2").name("Group 2").category(cat2).build();

            when(productGroupRepository.findById(200L)).thenReturn(Optional.of(group2));
            // Only product1 is found, product 9999 is missing
            when(productRepository.findAllByIdInAndDeletedAtIsNull(List.of(1001L, 9999L)))
                    .thenReturn(List.of(product1));

            assertThrows(ResourceNotFoundException.class, () ->
                    productTreeService.bulkMoveProducts(List.of(1001L, 9999L), 200L)
            );
            verify(productRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("bulkMoveProducts: Successfully bulk moves all products")
        void bulkMoveProducts_Success() {
            ProductGroup group2 = ProductGroup.builder().id(200L).code("PG2").name("Group 2").category(cat2).build();

            when(productGroupRepository.findById(200L)).thenReturn(Optional.of(group2));
            when(productRepository.findAllByIdInAndDeletedAtIsNull(List.of(1001L, 1002L)))
                    .thenReturn(new ArrayList<>(List.of(product1, product2)));

            BulkMoveProductsResponse response = productTreeService.bulkMoveProducts(List.of(1001L, 1002L), 200L);

            assertNotNull(response);
            assertEquals(2, response.getMovedCount());
            assertEquals(200L, response.getTargetGroupId());
            assertEquals(group2, product1.getProductGroup());
            assertEquals(cat2, product1.getCategory());
            assertEquals(group2, product2.getProductGroup());
            assertEquals(cat2, product2.getCategory());
            verify(productRepository, times(1)).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("Price Unification Tests")
    class PriceUnificationTests {

        @Test
        @DisplayName("setProductGroupPriceUnification: Toggles isPriceUnified successfully without changing prices")
        void setPriceUnification_Success() {
            when(productGroupRepository.findById(100L)).thenReturn(Optional.of(group1));

            productTreeService.setProductGroupPriceUnification(100L, true);

            assertTrue(group1.isPriceUnified());
            verify(productGroupRepository, times(1)).save(group1);
            verify(productRepository, never()).save(any());
            verify(productRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("setProductGroupPriceUnification: Throws 404 when group not found")
        void setPriceUnification_NotFound() {
            when(productGroupRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    productTreeService.setProductGroupPriceUnification(999L, true)
            );
        }

        @Test
        @DisplayName("getGroupPriceSummary: Returns summary with discrepancy detection")
        void getGroupPriceSummary_WithDiscrepancy() {
            com.mgh.backend.product.entity.ProductBarcode b1 = com.mgh.backend.product.entity.ProductBarcode.builder()
                    .id(1L)
                    .barcode("BAR1")
                    .sellingPrice(new java.math.BigDecimal("60.00"))
                    .buyingPrice(new java.math.BigDecimal("50.00"))
                    .isDefault(true)
                    .build();
            product1.setBarcodes(new ArrayList<>(List.of(b1)));

            com.mgh.backend.product.entity.ProductBarcode b2 = com.mgh.backend.product.entity.ProductBarcode.builder()
                    .id(2L)
                    .barcode("BAR2")
                    .sellingPrice(new java.math.BigDecimal("65.00"))
                    .buyingPrice(new java.math.BigDecimal("52.00"))
                    .isDefault(true)
                    .build();
            product2.setBarcodes(new ArrayList<>(List.of(b2)));

            when(productGroupRepository.findById(100L)).thenReturn(Optional.of(group1));
            when(productRepository.findAllWithBarcodesByProductGroupIdAndDeletedAtIsNull(100L))
                    .thenReturn(List.of(product1, product2));
            when(productMapper.activeBarcodes(product1)).thenReturn(List.of(b1));
            when(productMapper.findDefaultBarcode(List.of(b1))).thenReturn(b1);
            when(productMapper.activeBarcodes(product2)).thenReturn(List.of(b2));
            when(productMapper.findDefaultBarcode(List.of(b2))).thenReturn(b2);

            com.mgh.backend.product.dto.response.GroupPriceSummaryDto summary = productTreeService.getGroupPriceSummary(100L);

            assertNotNull(summary);
            assertEquals(100L, summary.getGroupId());
            assertEquals(2, summary.getProductCount());
            assertEquals(2, summary.getDistinctSellingPrices().size());
            assertTrue(summary.isHasPriceDiscrepancy());
        }

        @Test
        @DisplayName("updateGroupSellingPrice: Updates selling price on all barcodes, leaves buying price untouched")
        void updateGroupSellingPrice_Success() {
            com.mgh.backend.product.entity.ProductBarcode b1 = com.mgh.backend.product.entity.ProductBarcode.builder()
                    .id(1L)
                    .barcode("BAR1")
                    .sellingPrice(new java.math.BigDecimal("60.00"))
                    .buyingPrice(new java.math.BigDecimal("50.00"))
                    .isDefault(true)
                    .build();
            product1.setBarcodes(new ArrayList<>(List.of(b1)));

            com.mgh.backend.product.entity.ProductBarcode b2 = com.mgh.backend.product.entity.ProductBarcode.builder()
                    .id(2L)
                    .barcode("BAR2")
                    .sellingPrice(new java.math.BigDecimal("65.00"))
                    .buyingPrice(new java.math.BigDecimal("52.00"))
                    .isDefault(true)
                    .build();
            product2.setBarcodes(new ArrayList<>(List.of(b2)));

            when(productGroupRepository.findById(100L)).thenReturn(Optional.of(group1));
            when(productRepository.findAllWithBarcodesByProductGroupIdAndDeletedAtIsNull(100L))
                    .thenReturn(List.of(product1, product2));
            when(productMapper.activeBarcodes(product1)).thenReturn(List.of(b1));
            when(productMapper.activeBarcodes(product2)).thenReturn(List.of(b2));

            productTreeService.updateGroupSellingPrice(100L, new java.math.BigDecimal("70.00"));

            assertEquals(new java.math.BigDecimal("70.00"), b1.getSellingPrice());
            assertEquals(new java.math.BigDecimal("50.00"), b1.getBuyingPrice()); // buying price untouched!
            assertEquals(new java.math.BigDecimal("70.00"), b2.getSellingPrice());
            assertEquals(new java.math.BigDecimal("52.00"), b2.getBuyingPrice()); // buying price untouched!
            verify(productRepository, times(1)).saveAll(anyList());
        }
    }
}
