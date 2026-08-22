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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PosMigrationServiceImpl implements PosMigrationService {

    private static final Logger log = LoggerFactory.getLogger(PosMigrationServiceImpl.class);

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
                    .alreadyExisting(0)
                    .failedRecords(0)
                    .failures(new ArrayList<>())
                    .build();
        }

        int totalRecords = items.size();
        List<ValidationError> failures = new ArrayList<>();
        Set<String> failedCodes = new HashSet<>();
        int alreadyExisting = 0;

        // ── STEP 1: BULK PRELOAD ─────────────────────────────────────────────────────
        // Load all existing entities once. These maps serve dual purpose:
        //   (a) existence check: is this code already in the DB?
        //   (b) parent resolution: what entity do I link children to?
        Map<String, ProductCategory> categoriesByCode = new HashMap<>();
        categoryRepository.findAllWithCode()
                .forEach(c -> categoriesByCode.put(c.getCode(), c));

        Map<String, Brand> brandsByCode = new HashMap<>();
        brandRepository.findAllWithCategory()
                .forEach(b -> brandsByCode.put(b.getCode(), b));

        Map<String, ProductGroup> productGroupsByCode = new HashMap<>();
        productGroupRepository.findAllWithBrand()
                .forEach(pg -> productGroupsByCode.put(pg.getCode(), pg));

        // Products have no children, so we only need barcode→name for existence checks.
        Map<String, String> existingProductCodeToName = new HashMap<>();
        for (Object[] row : productRepository.findAllBarcodeAndNamePairs()) {
            if (row[0] != null) {
                existingProductCodeToName.put((String) row[0], row[1] != null ? (String) row[1] : "");
            }
        }

        // ── STEP 2: BUILD INPUT INDEX ─────────────────────────────────────────────────
        Map<String, PosDataItemDto> itemsByCode = new HashMap<>();
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

        // ── STEP 3: FIELD PRESENCE VALIDATION ────────────────────────────────────────
        for (Map.Entry<String, PosDataItemDto> entry : itemsByCode.entrySet()) {
            String code = entry.getKey();
            if (failedCodes.contains(code)) continue;
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
            }
        }

        // Identify parent-child usage across the batch
        Set<String> parentOfGroupingNodes = new HashSet<>();
        Set<String> parentOfProducts = new HashSet<>();

        for (Map.Entry<String, PosDataItemDto> entry : itemsByCode.entrySet()) {
            if (failedCodes.contains(entry.getKey())) continue;
            PosDataItemDto item = entry.getValue();
            String parentCode = item.getItemPrnt().trim();
            if (!parentCode.equals("0")) {
                if (item.getItemType() == 1) {
                    parentOfGroupingNodes.add(parentCode);
                } else if (item.getItemType() == 2) {
                    parentOfProducts.add(parentCode);
                }
            }
        }

        // ── STEP 4: HIERARCHY TRACE & DEPTH CALCULATION ──────────────────────────────
        // Computes hops-from-root for dependency ordering without fixed hop assumptions.
        Map<String, Integer> itemHops = new HashMap<>();

        for (Map.Entry<String, PosDataItemDto> entry : itemsByCode.entrySet()) {
            String code = entry.getKey();
            if (failedCodes.contains(code)) continue;
            PosDataItemDto item = entry.getValue();

            List<String> path = new ArrayList<>();
            PosDataItemDto current = item;
            boolean hasCircle = false;
            boolean parentNotFound = false;
            boolean parentFailed = false;
            boolean invalidParentType = false;
            String invalidParentCode = null;
            int invalidParentTypeVal = 0;
            int hops = 0;

            if (item.getItemType() == 2 && item.getItemPrnt().trim().equals("0")) {
                failures.add(new ValidationError(code, "Product (ItemType=2) cannot be a root record (ItemPrnt=0)"));
                failedCodes.add(code);
                continue;
            }

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

                PosDataItemDto nextInInput = itemsByCode.get(parentCode);
                if (nextInInput == null) {
                    // Parent not in this payload — resolve depth from preloaded DB maps
                    int parentLevel = -1;
                    if (categoriesByCode.containsKey(parentCode)) {
                        parentLevel = 0;
                    } else if (brandsByCode.containsKey(parentCode)) {
                        parentLevel = 1;
                    } else if (productGroupsByCode.containsKey(parentCode)) {
                        parentLevel = 2;
                    }

                    if (parentLevel == -1) {
                        parentNotFound = true;
                    } else {
                        hops += (parentLevel + 1);
                    }
                    current = null;
                } else {
                    if (nextInInput.getItemType() != null && nextInInput.getItemType() != 1) {
                        invalidParentType = true;
                        invalidParentCode = parentCode;
                        invalidParentTypeVal = nextInInput.getItemType();
                        break;
                    }
                    current = nextInInput;
                    hops++;
                }
            }

            if (invalidParentType) {
                failures.add(new ValidationError(code,
                        "Parent '" + invalidParentCode + "' has invalid ItemType=" + invalidParentTypeVal + ". Parents must be grouping nodes (ItemType=1)"));
                failedCodes.add(code);
                continue;
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
        }

        // ── STEP 5: PROCESS GROUPING NODES (ItemType = 1) IN TOPOLOGICAL ORDER ──────
        // Sort ItemType = 1 items by hops-from-root ascending so parents are always processed before children.
        List<PosDataItemDto> groupingItems = new ArrayList<>();
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (!failedCodes.contains(code) && item.getItemType() == 1 && itemHops.containsKey(code)) {
                groupingItems.add(item);
            }
        }
        groupingItems.sort(Comparator.comparingInt(i -> itemHops.get(i.getItemCode().trim())));

        for (PosDataItemDto item : groupingItems) {
            String code = item.getItemCode().trim();
            if (failedCodes.contains(code)) continue;

            int depth = itemHops.get(code);
            String incomingName = item.getItemName().trim();
            String parentCode = item.getItemPrnt().trim();

            if (depth == 0) {
                // ── Root Category (ItemPrnt = "0") ──
                if (categoriesByCode.containsKey(code)) {
                    if (categoriesByCode.get(code).getName().equals(incomingName)) {
                        alreadyExisting++;
                        log.info("Category already exists: code={}, name={}", code, incomingName);
                    } else {
                        String conflict = "Category code '" + code + "' already exists with a different name: '"
                                + categoriesByCode.get(code).getName() + "'";
                        failures.add(new ValidationError(code, conflict));
                        failedCodes.add(code);
                        log.warn("Category import failed: code={}, reason={}", code, conflict);
                    }
                    continue;
                }

                try {
                    ProductCategory saved = posMigrationPersister.saveCategory(
                            ProductCategory.builder().name(incomingName).code(code).build());
                    categoriesByCode.put(code, saved);
                    log.info("Category created: code={}, name={}", code, incomingName);
                } catch (Exception e) {
                    String reason = "Failed to persist Category: " + e.getMessage();
                    failures.add(new ValidationError(code, reason));
                    failedCodes.add(code);
                    log.warn("Category import failed: code={}, reason={}", code, reason);
                }
            } else {
                // ── Non-root Grouping Node (depth >= 1) ──
                // Determine whether this node acts as Brand, ProductGroup, or both
                boolean hasChildGroups = parentOfGroupingNodes.contains(code);
                boolean hasChildProducts = parentOfProducts.contains(code);
                // If neither child type is detected in current batch, treat as ProductGroup by default
                boolean actAsBrand = hasChildGroups;
                boolean actAsProductGroup = hasChildProducts || !hasChildGroups;

                // Resolve ancestor Category & Brand
                ProductCategory ancestorCategory = null;
                Brand parentBrand = null;

                if (brandsByCode.containsKey(parentCode)) {
                    parentBrand = brandsByCode.get(parentCode);
                    ancestorCategory = parentBrand.getCategory();
                } else if (categoriesByCode.containsKey(parentCode)) {
                    ancestorCategory = categoriesByCode.get(parentCode);
                } else if (productGroupsByCode.containsKey(parentCode)) {
                    ProductGroup parentPg = productGroupsByCode.get(parentCode);
                    ancestorCategory = parentPg.getCategory() != null ? parentPg.getCategory() :
                            (parentPg.getBrand() != null ? parentPg.getBrand().getCategory() : null);
                }

                if (ancestorCategory == null) {
                    String reason = "Parent grouping node with code '" + parentCode + "' was not found or failed validation";
                    failures.add(new ValidationError(code, reason));
                    failedCodes.add(code);
                    log.warn("Grouping node import failed: code={}, reason={}", code, reason);
                    continue;
                }

                boolean itemCounted = false;

                // 1. Create/Check Brand (if intermediate node)
                if (actAsBrand) {
                    if (brandsByCode.containsKey(code)) {
                        if (brandsByCode.get(code).getName().equals(incomingName)) {
                            if (!actAsProductGroup && !itemCounted) {
                                alreadyExisting++;
                                itemCounted = true;
                            }
                            log.info("Brand already exists: code={}, name={}", code, incomingName);
                        } else {
                            String conflict = "Brand code '" + code + "' already exists with a different name: '"
                                    + brandsByCode.get(code).getName() + "'";
                            failures.add(new ValidationError(code, conflict));
                            failedCodes.add(code);
                            log.warn("Brand import failed: code={}, reason={}", code, conflict);
                            continue;
                        }
                    } else {
                        try {
                            Brand savedBrand = posMigrationPersister.saveBrand(
                                    Brand.builder().name(incomingName).code(code).category(ancestorCategory).build());
                            brandsByCode.put(code, savedBrand);
                            log.info("Brand created: code={}, name={}", code, incomingName);
                        } catch (Exception e) {
                            String reason = "Failed to persist Brand: " + e.getMessage();
                            failures.add(new ValidationError(code, reason));
                            failedCodes.add(code);
                            log.warn("Brand import failed: code={}, reason={}", code, reason);
                            continue;
                        }
                    }
                }

                // 2. Create/Check ProductGroup (if direct parent of products or leaf node)
                if (actAsProductGroup) {
                    if (productGroupsByCode.containsKey(code)) {
                        if (productGroupsByCode.get(code).getName().equals(incomingName)) {
                            if (!itemCounted) {
                                alreadyExisting++;
                                itemCounted = true;
                            }
                            log.info("ProductGroup already exists: code={}, name={}", code, incomingName);
                        } else {
                            String conflict = "ProductGroup code '" + code + "' already exists with a different name: '"
                                    + productGroupsByCode.get(code).getName() + "'";
                            failures.add(new ValidationError(code, conflict));
                            failedCodes.add(code);
                            log.warn("ProductGroup import failed: code={}, reason={}", code, conflict);
                        }
                    } else {
                        try {
                            ProductGroup savedPg = posMigrationPersister.saveProductGroup(
                                    ProductGroup.builder()
                                            .name(incomingName)
                                            .code(code)
                                            .brand(parentBrand)
                                            .category(ancestorCategory)
                                            .build());
                            productGroupsByCode.put(code, savedPg);
                            log.info("ProductGroup created: code={}, name={}", code, incomingName);
                        } catch (Exception e) {
                            String reason = "Failed to persist ProductGroup: " + e.getMessage();
                            failures.add(new ValidationError(code, reason));
                            failedCodes.add(code);
                            log.warn("ProductGroup import failed: code={}, reason={}", code, reason);
                        }
                    }
                }
            }
        }

        // ── STEP 6: PROCESS PRODUCTS (ItemType = 2) ──────────────────────────────────
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (failedCodes.contains(code) || item.getItemType() != 2) continue;

            String incomingName = item.getItemName().trim();

            if (existingProductCodeToName.containsKey(code)) {
                if (existingProductCodeToName.get(code).equals(incomingName)) {
                    alreadyExisting++;
                    log.info("Product already exists: sku={}", code);
                } else {
                    String conflict = "Product barcode '" + code + "' already exists with a different name: '"
                            + existingProductCodeToName.get(code) + "'";
                    failures.add(new ValidationError(code, conflict));
                    failedCodes.add(code);
                    log.warn("Product import failed: sku={}, reason={}", code, conflict);
                }
                continue;
            }

            String parentCode = item.getItemPrnt().trim();
            ProductGroup parentPg = productGroupsByCode.get(parentCode);
            ProductCategory parentCat = null;

            if (parentPg != null) {
                parentCat = parentPg.getCategory() != null ? parentPg.getCategory() :
                        (parentPg.getBrand() != null ? parentPg.getBrand().getCategory() : null);
            } else if (categoriesByCode.containsKey(parentCode)) {
                parentCat = categoriesByCode.get(parentCode);
            } else if (brandsByCode.containsKey(parentCode)) {
                parentCat = brandsByCode.get(parentCode).getCategory();
            }

            if (parentPg == null && parentCat == null) {
                String reason = "Parent ProductGroup with code '" + parentCode + "' was not found or failed validation";
                failures.add(new ValidationError(code, reason));
                failedCodes.add(code);
                log.warn("Product import failed: sku={}, reason={}", code, reason);
                continue;
            }

            try {
                BigDecimal buyingPrice  = item.getItemPrice0() != null ? item.getItemPrice0() : BigDecimal.ZERO;
                BigDecimal sellingPrice = item.getItemPrice1() != null ? item.getItemPrice1() : BigDecimal.ZERO;

                Product product = Product.builder()
                        .barcode(code)
                        .baseName(incomingName)
                        .name(incomingName)
                        .type(ProductType.INVENTORY)
                        .status(ProductStatus.ACTIVE)
                        .productGroup(parentPg)
                        .category(parentCat)
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
                existingProductCodeToName.put(code, incomingName);
                log.info("Product created: sku={}, name={}", code, incomingName);
            } catch (Exception e) {
                String reason = "Failed to persist Product: " + e.getMessage();
                failures.add(new ValidationError(code, reason));
                failedCodes.add(code);
                log.warn("Product import failed: sku={}, reason={}", code, reason);
            }
        }

        // ── STEP 7: BUILD MIGRATION SUMMARY ──────────────────────────────────────────
        int failedRecords = failures.size();
        int successfulRecords = totalRecords - alreadyExisting - failedRecords;

        return MigrationResultDto.builder()
                .success(true)
                .totalRecords(totalRecords)
                .successfulRecords(successfulRecords)
                .alreadyExisting(alreadyExisting)
                .failedRecords(failedRecords)
                .failures(failures)
                .build();
    }
}
