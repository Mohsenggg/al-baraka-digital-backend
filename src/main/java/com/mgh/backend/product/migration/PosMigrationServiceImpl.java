package com.mgh.backend.product.migration;

import com.mgh.backend.product.entity.Brand;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.ProductGroup;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import com.mgh.backend.product.migration.MigrationErrorDto.ValidationError;
import com.mgh.backend.product.repository.BrandRepository;
import com.mgh.backend.product.repository.ProductBarcodeRepository;
import com.mgh.backend.product.repository.ProductCategoryRepository;
import com.mgh.backend.product.repository.ProductGroupRepository;
import com.mgh.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PosMigrationServiceImpl implements PosMigrationService {

    private final ProductCategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductGroupRepository productGroupRepository;
    private final ProductRepository productRepository;
    private final ProductBarcodeRepository productBarcodeRepository;

    @Override
    @Transactional
    public MigrationResultDto importPosData(List<PosDataItemDto> items) {
        if (items == null || items.isEmpty()) {
            return MigrationResultDto.builder()
                    .success(true)
                    .message("No records to process")
                    .categoriesCreated(0)
                    .brandsCreated(0)
                    .productGroupsCreated(0)
                    .productsCreated(0)
                    .totalRecordsProcessed(0)
                    .build();
        }

        // Build item map for easy lookup
        Map<String, PosDataItemDto> itemsByCode = new HashMap<>();
        List<ValidationError> errors = new ArrayList<>();

        for (PosDataItemDto item : items) {
            if (item.getItemCode() == null || item.getItemCode().trim().isEmpty()) {
                errors.add(new ValidationError(null, "ItemCode is missing or empty"));
                continue;
            }
            String code = item.getItemCode().trim();
            if (itemsByCode.containsKey(code)) {
                errors.add(new ValidationError(code, "Duplicate ItemCode '" + code + "' in request body"));
            } else {
                itemsByCode.put(code, item);
            }
        }

        // Return early if basic mapping is broken
        if (!errors.isEmpty()) {
            throw new PosMigrationException("Validation failed", errors);
        }

        // Load existing database entities to prevent duplicates
        Set<String> existingCategoryCodes = categoryRepository.findAllCodes();
        Set<String> existingBrandCodes = brandRepository.findAllCodes();
        Set<String> existingProductGroupCodes = productGroupRepository.findAllCodes();
        Set<String> existingProductBarcodes = productRepository.findAllProductBarcodes();
        Set<String> existingBarcodeBarcodes = productBarcodeRepository.findAllBarcodeBarcodes();

        // Level detection and structural validation
        Map<String, Integer> itemHops = new HashMap<>();

        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            
            if (item.getItemPrnt() == null || item.getItemPrnt().trim().isEmpty()) {
                errors.add(new ValidationError(code, "ItemPrnt is missing or empty"));
                continue;
            }
            String prnt = item.getItemPrnt().trim();

            if (item.getItemName() == null || item.getItemName().trim().isEmpty()) {
                errors.add(new ValidationError(code, "ItemName is missing or empty"));
                continue;
            }

            if (item.getItemType() == null || (item.getItemType() != 1 && item.getItemType() != 2)) {
                errors.add(new ValidationError(code, "Invalid ItemType. Expected 1 or 2, got: " + item.getItemType()));
                continue;
            }

            // Path tracing & circular dependency detection
            List<String> path = new ArrayList<>();
            PosDataItemDto current = item;
            boolean hasCircle = false;
            boolean parentNotFound = false;
            int hops = 0;

            while (current != null && !current.getItemPrnt().trim().equals("0")) {
                String parentCode = current.getItemPrnt().trim();
                if (path.contains(parentCode)) {
                    hasCircle = true;
                    break;
                }
                path.add(current.getItemCode().trim());
                current = itemsByCode.get(parentCode);
                if (current == null) {
                    parentNotFound = true;
                    break;
                }
                hops++;
            }

            if (hasCircle) {
                errors.add(new ValidationError(code, "Circular parent relationship detected starting at code " + code));
                continue;
            }
            if (parentNotFound) {
                errors.add(new ValidationError(code, "Parent with code '" + item.getItemPrnt() + "' was not found"));
                continue;
            }

            itemHops.put(code, hops);

            // Level type verification and db uniqueness check
            if (hops == 0) { // Category
                if (item.getItemType() != 1) {
                    errors.add(new ValidationError(code, "Category (hops=0) must have ItemType = 1"));
                }
                if (existingCategoryCodes.contains(code)) {
                    errors.add(new ValidationError(code, "Category with code '" + code + "' already exists in database"));
                }
            } else if (hops == 1) { // Brand
                if (item.getItemType() != 1) {
                    errors.add(new ValidationError(code, "Brand (hops=1) must have ItemType = 1"));
                }
                if (existingBrandCodes.contains(code)) {
                    errors.add(new ValidationError(code, "Brand with code '" + code + "' already exists in database"));
                }
            } else if (hops == 2) { // ProductGroup
                if (item.getItemType() != 1) {
                    errors.add(new ValidationError(code, "ProductGroup (hops=2) must have ItemType = 1"));
                }
                if (existingProductGroupCodes.contains(code)) {
                    errors.add(new ValidationError(code, "ProductGroup with code '" + code + "' already exists in database"));
                }
            } else if (hops == 3) { // Product
                if (item.getItemType() != 2) {
                    errors.add(new ValidationError(code, "Product (hops=3) must have ItemType = 2"));
                }
                if (existingProductBarcodes.contains(code) || existingBarcodeBarcodes.contains(code)) {
                    errors.add(new ValidationError(code, "Product with barcode '" + code + "' already exists in database"));
                }
            } else {
                errors.add(new ValidationError(code, "Invalid hierarchy depth. Expected Category (0), Brand (1), ProductGroup (2), or Product (3). Got hops: " + hops));
            }
        }

        if (!errors.isEmpty()) {
            throw new PosMigrationException("Validation failed", errors);
        }

        // Success - start persisting hierarchy
        Map<String, ProductCategory> categoriesByCode = new HashMap<>();
        Map<String, Brand> brandsByCode = new HashMap<>();
        Map<String, ProductGroup> productGroupsByCode = new HashMap<>();
        List<Product> productsToSave = new ArrayList<>();

        List<ProductCategory> categoriesToSave = new ArrayList<>();
        List<Brand> brandsToSave = new ArrayList<>();
        List<ProductGroup> productGroupsToSave = new ArrayList<>();

        // Level 1: Categories
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (itemHops.get(code) == 0) {
                ProductCategory category = ProductCategory.builder()
                        .name(item.getItemName().trim())
                        .code(code)
                        .build();
                categoriesToSave.add(category);
            }
        }
        List<ProductCategory> savedCategories = categoryRepository.saveAll(categoriesToSave);
        for (ProductCategory cat : savedCategories) {
            categoriesByCode.put(cat.getCode(), cat);
        }

        // Level 2: Brands
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (itemHops.get(code) == 1) {
                ProductCategory parentCat = categoriesByCode.get(item.getItemPrnt().trim());
                Brand brand = Brand.builder()
                        .name(item.getItemName().trim())
                        .code(code)
                        .category(parentCat)
                        .build();
                brandsToSave.add(brand);
            }
        }
        List<Brand> savedBrands = brandRepository.saveAll(brandsToSave);
        for (Brand b : savedBrands) {
            brandsByCode.put(b.getCode(), b);
        }

        // Level 3: ProductGroups
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (itemHops.get(code) == 2) {
                Brand parentBrand = brandsByCode.get(item.getItemPrnt().trim());
                ProductGroup pg = ProductGroup.builder()
                        .name(item.getItemName().trim())
                        .code(code)
                        .brand(parentBrand)
                        .build();
                productGroupsToSave.add(pg);
            }
        }
        List<ProductGroup> savedGroups = productGroupRepository.saveAll(productGroupsToSave);
        for (ProductGroup pg : savedGroups) {
            productGroupsByCode.put(pg.getCode(), pg);
        }

        // Level 4: Products
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (itemHops.get(code) == 3) {
                ProductGroup parentPg = productGroupsByCode.get(item.getItemPrnt().trim());
                
                // Buying and selling price logic:
                // buyingPrice = ItemPrice0, sellingPrice = ItemPrice1
                BigDecimal buyingPrice = item.getItemPrice0() != null ? item.getItemPrice0() : BigDecimal.ZERO;
                BigDecimal sellingPrice = item.getItemPrice1() != null ? item.getItemPrice1() : BigDecimal.ZERO;

                Product product = Product.builder()
                        .barcode(code)
                        .baseName(item.getItemName().trim())
                        .name(item.getItemName().trim())
                        .type(ProductType.INVENTORY)
                        .status(ProductStatus.ACTIVE)
                        .productGroup(parentPg)
                        .category(parentPg.getBrand().getCategory()) // keep compatible with category-only queries
                        .minStockLevel(item.getItemMinStock() != null ? item.getItemMinStock() : 0.0)
                        .maxStockLevel(item.getItemMaxStock() != null ? item.getItemMaxStock() : 0.0)
                        .barcodes(new ArrayList<>())
                        .build();

                ProductBarcode pb = ProductBarcode.builder()
                        .product(product)
                        .barcode(code)
                        .buyingPrice(buyingPrice)
                        .sellingPrice(sellingPrice)
                        .stock(0.0)
                        .isDefault(true)
                        .build();

                product.getBarcodes().add(pb);
                productsToSave.add(product);
            }
        }
        productRepository.saveAll(productsToSave);

        return MigrationResultDto.builder()
                .success(true)
                .message("POS data imported successfully")
                .categoriesCreated(categoriesToSave.size())
                .brandsCreated(brandsToSave.size())
                .productGroupsCreated(productGroupsToSave.size())
                .productsCreated(productsToSave.size())
                .totalRecordsProcessed(items.size())
                .build();
    }
}
