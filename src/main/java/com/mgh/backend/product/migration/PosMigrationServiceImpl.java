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
    private final PosMigrationPersister posMigrationPersister;

    @Override
    public MigrationResultDto importPosData(List<PosDataItemDto> items) {
        if (items == null || items.isEmpty()) {
            return MigrationResultDto.builder()
                    .success(true)
                    .totalRecords(0)
                    .successfulRecords(0)
                    .failedRecords(0)
                    .failures(new ArrayList<>())
                    .build();
        }

        int totalRecords = items.size();
        List<ValidationError> failures = new ArrayList<>();
        Set<String> failedCodes = new HashSet<>();

        // Map from ItemCode to DTO for easy hierarchy lookup
        Map<String, PosDataItemDto> itemsByCode = new HashMap<>();

        // 1. Basic validation and duplicate check in request body
        for (PosDataItemDto item : items) {
            if (item.getItemCode() == null || item.getItemCode().trim().isEmpty()) {
                failures.add(new ValidationError(null, "ItemCode is missing or empty"));
                continue;
            }
            String code = item.getItemCode().trim();
            if (itemsByCode.containsKey(code)) {
                failures.add(new ValidationError(code, "Conflicting duplicate ItemCode"));
                failedCodes.add(code);
                continue;
            }
            itemsByCode.put(code, item);
        }

        // 2. Field presence and basic structure validation
        for (Map.Entry<String, PosDataItemDto> entry : itemsByCode.entrySet()) {
            String code = entry.getKey();
            if (failedCodes.contains(code)) {
                continue;
            }
            PosDataItemDto item = entry.getValue();

            if (item.getItemPrnt() == null || item.getItemPrnt().trim().isEmpty()) {
                failures.add(new ValidationError(code, "ItemPrnt is missing or empty"));
                failedCodes.add(code);
                continue;
            }
            if (item.getItemName() == null || item.getItemName().trim().isEmpty()) {
                failures.add(new ValidationError(code, "ItemName is missing or empty"));
                failedCodes.add(code);
                continue;
            }
            if (item.getItemType() == null || (item.getItemType() != 1 && item.getItemType() != 2)) {
                failures.add(new ValidationError(code, "Invalid ItemType. Expected 1 or 2, got: " + item.getItemType()));
                failedCodes.add(code);
                continue;
            }
        }

        // 3. Hierarchy trace and hops computation
        Map<String, Integer> itemHops = new HashMap<>();
        for (Map.Entry<String, PosDataItemDto> entry : itemsByCode.entrySet()) {
            String code = entry.getKey();
            if (failedCodes.contains(code)) {
                continue;
            }
            PosDataItemDto item = entry.getValue();

            List<String> path = new ArrayList<>();
            PosDataItemDto current = item;
            boolean hasCircle = false;
            boolean parentNotFound = false;
            boolean parentFailed = false;
            int hops = 0;

            while (current != null && !current.getItemPrnt().trim().equals("0")) {
                String parentCode = current.getItemPrnt().trim();
                if (failedCodes.contains(parentCode)) {
                    parentFailed = true;
                    break;
                }
                if (path.contains(parentCode)) {
                    hasCircle = true;
                    break;
                }
                path.add(current.getItemCode().trim());

                PosDataItemDto nextParent = itemsByCode.get(parentCode);
                if (nextParent == null) {
                    // Parent not in input. Check if parent exists in database at any level
                    int parentHops = -1;
                    if (categoryRepository.existsByCode(parentCode)) {
                        parentHops = 0;
                    } else if (brandRepository.existsByCode(parentCode)) {
                        parentHops = 1;
                    } else if (productGroupRepository.existsByCode(parentCode)) {
                        parentHops = 2;
                    }

                    if (parentHops == -1) {
                        parentNotFound = true;
                    } else {
                        hops += (parentHops + 1);
                    }
                    current = null;
                } else {
                    current = nextParent;
                    hops++;
                }
            }

            if (parentFailed) {
                failures.add(new ValidationError(code, "Parent with code '" + item.getItemPrnt() + "' failed validation/import"));
                failedCodes.add(code);
                continue;
            }
            if (hasCircle) {
                failures.add(new ValidationError(code, "Circular parent relationship detected starting at code " + code));
                failedCodes.add(code);
                continue;
            }
            if (parentNotFound) {
                failures.add(new ValidationError(code, "Parent with code '" + item.getItemPrnt() + "' was not found in input or database"));
                failedCodes.add(code);
                continue;
            }

            itemHops.put(code, hops);

            // Level type verification
            if (hops == 0) { // Category
                if (item.getItemType() != 1) {
                    failures.add(new ValidationError(code, "Category (hops=0) must have ItemType = 1"));
                    failedCodes.add(code);
                }
            } else if (hops == 1) { // Brand
                if (item.getItemType() != 1) {
                    failures.add(new ValidationError(code, "Brand (hops=1) must have ItemType = 1"));
                    failedCodes.add(code);
                }
            } else if (hops == 2) { // ProductGroup
                if (item.getItemType() != 1) {
                    failures.add(new ValidationError(code, "ProductGroup (hops=2) must have ItemType = 1"));
                    failedCodes.add(code);
                }
            } else if (hops == 3) { // Product
                if (item.getItemType() != 2) {
                    failures.add(new ValidationError(code, "Product (hops=3) must have ItemType = 2"));
                    failedCodes.add(code);
                }
            } else {
                failures.add(new ValidationError(code, "Invalid hierarchy depth. Got hops: " + hops));
                failedCodes.add(code);
            }
        }

        // Cache for successfully resolved entities
        Map<String, ProductCategory> categoriesByCode = new HashMap<>();
        Map<String, Brand> brandsByCode = new HashMap<>();
        Map<String, ProductGroup> productGroupsByCode = new HashMap<>();

        // Level 0: Category Import
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (failedCodes.contains(code) || itemHops.get(code) == null || itemHops.get(code) != 0) {
                continue;
            }

            if (categoryRepository.existsByCode(code)) {
                failures.add(new ValidationError(code, "Category with code '" + code + "' already exists in database"));
                failedCodes.add(code);
                continue;
            }

            try {
                ProductCategory category = ProductCategory.builder()
                        .name(item.getItemName().trim())
                        .code(code)
                        .build();
                ProductCategory saved = posMigrationPersister.saveCategory(category);
                categoriesByCode.put(code, saved);
            } catch (Exception e) {
                failures.add(new ValidationError(code, "Failed to persist Category: " + e.getMessage()));
                failedCodes.add(code);
            }
        }

        // Level 1: Brand Import
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (failedCodes.contains(code) || itemHops.get(code) == null || itemHops.get(code) != 1) {
                continue;
            }

            if (brandRepository.existsByCode(code)) {
                failures.add(new ValidationError(code, "Brand with code '" + code + "' already exists in database"));
                failedCodes.add(code);
                continue;
            }

            String parentCode = item.getItemPrnt().trim();
            ProductCategory parentCat = categoriesByCode.get(parentCode);
            if (parentCat == null) {
                parentCat = categoryRepository.findByCode(parentCode).orElse(null);
            }

            if (parentCat == null) {
                failures.add(new ValidationError(code, "Parent Category with code '" + parentCode + "' was not found or failed validation"));
                failedCodes.add(code);
                continue;
            }

            try {
                Brand brand = Brand.builder()
                        .name(item.getItemName().trim())
                        .code(code)
                        .category(parentCat)
                        .build();
                Brand saved = posMigrationPersister.saveBrand(brand);
                brandsByCode.put(code, saved);
            } catch (Exception e) {
                failures.add(new ValidationError(code, "Failed to persist Brand: " + e.getMessage()));
                failedCodes.add(code);
            }
        }

        // Level 2: ProductGroup Import
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (failedCodes.contains(code) || itemHops.get(code) == null || itemHops.get(code) != 2) {
                continue;
            }

            if (productGroupRepository.existsByCode(code)) {
                failures.add(new ValidationError(code, "ProductGroup with code '" + code + "' already exists in database"));
                failedCodes.add(code);
                continue;
            }

            String parentCode = item.getItemPrnt().trim();
            Brand parentBrand = brandsByCode.get(parentCode);
            if (parentBrand == null) {
                parentBrand = brandRepository.findByCode(parentCode).orElse(null);
            }

            if (parentBrand == null) {
                failures.add(new ValidationError(code, "Parent Brand with code '" + parentCode + "' was not found or failed validation"));
                failedCodes.add(code);
                continue;
            }

            try {
                ProductGroup pg = ProductGroup.builder()
                        .name(item.getItemName().trim())
                        .code(code)
                        .brand(parentBrand)
                        .build();
                ProductGroup saved = posMigrationPersister.saveProductGroup(pg);
                productGroupsByCode.put(code, saved);
            } catch (Exception e) {
                failures.add(new ValidationError(code, "Failed to persist ProductGroup: " + e.getMessage()));
                failedCodes.add(code);
            }
        }

        // Level 3: Product Import
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (failedCodes.contains(code) || itemHops.get(code) == null || itemHops.get(code) != 3) {
                continue;
            }

            if (productRepository.existsByBarcodeAndDeletedAtIsNull(code) || 
                productBarcodeRepository.existsByBarcodeAndDeletedAtIsNull(code)) {
                failures.add(new ValidationError(code, "Product with barcode '" + code + "' already exists in database"));
                failedCodes.add(code);
                continue;
            }

            String parentCode = item.getItemPrnt().trim();
            ProductGroup parentPg = productGroupsByCode.get(parentCode);
            if (parentPg == null) {
                parentPg = productGroupRepository.findByCode(parentCode).orElse(null);
            }

            if (parentPg == null) {
                failures.add(new ValidationError(code, "Parent ProductGroup with code '" + parentCode + "' was not found or failed validation"));
                failedCodes.add(code);
                continue;
            }

            try {
                BigDecimal buyingPrice = item.getItemPrice0() != null ? item.getItemPrice0() : BigDecimal.ZERO;
                BigDecimal sellingPrice = item.getItemPrice1() != null ? item.getItemPrice1() : BigDecimal.ZERO;

                Product product = Product.builder()
                        .barcode(code)
                        .baseName(item.getItemName().trim())
                        .name(item.getItemName().trim())
                        .type(ProductType.INVENTORY)
                        .status(ProductStatus.ACTIVE)
                        .productGroup(parentPg)
                        .category(parentPg.getBrand().getCategory())
                        .minStockLevel(item.getItemMinStock() != null ? item.getItemMinStock() : 0.0)
                        .maxStockLevel(item.getItemMaxStock() != null ? item.getItemMaxStock() : 0.0)
                        .barcodes(new ArrayList<>())
                        .build();

                ProductBarcode pb = ProductBarcode.builder()
                        .barcode(code)
                        .buyingPrice(buyingPrice)
                        .sellingPrice(sellingPrice)
                        .stock(0.0)
                        .isDefault(true)
                        .build();

                posMigrationPersister.saveProduct(product, pb);
            } catch (Exception e) {
                failures.add(new ValidationError(code, "Failed to persist Product: " + e.getMessage()));
                failedCodes.add(code);
            }
        }

        int failedRecords = failures.size();
        int successfulRecords = totalRecords - failedRecords;

        return MigrationResultDto.builder()
                .success(true)
                .totalRecords(totalRecords)
                .successfulRecords(successfulRecords)
                .failedRecords(failedRecords)
                .failures(failures)
                .build();
    }
}
