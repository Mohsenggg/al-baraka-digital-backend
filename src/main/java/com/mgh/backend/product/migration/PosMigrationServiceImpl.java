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
        // New records inserted during this migration are added to the maps as they are
        // saved, so children can resolve parents regardless of insertion order.

        Map<String, ProductCategory> categoriesByCode = new HashMap<>();
        categoryRepository.findAllWithCode()
                .forEach(c -> categoriesByCode.put(c.getCode(), c));

        Map<String, Brand> brandsByCode = new HashMap<>();
        brandRepository.findAllWithCategory()
                .forEach(b -> brandsByCode.put(b.getCode(), b));

        Map<String, ProductGroup> productGroupsByCode = new HashMap<>();
        productGroupRepository.findAllWithBrand()
                .forEach(pg -> productGroupsByCode.put(pg.getCode(), pg));

        // Products have no children, so we only need code→name for existence checks.
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

        // ── STEP 4: HIERARCHY TRACE (computes depth of each item) ────────────────────
        // Uses preloaded maps for parent lookups — no per-record DB queries.
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

                PosDataItemDto nextInInput = itemsByCode.get(parentCode);
                if (nextInInput == null) {
                    // Parent not in this payload — resolve level from preloaded maps
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
                    current = nextInInput;
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

            // Level ↔ ItemType consistency
            if (hops == 0 && item.getItemType() != 1) {
                failures.add(new ValidationError(code, "Category (hops=0) must have ItemType = 1"));
                failedCodes.add(code);
            } else if (hops == 1 && item.getItemType() != 1) {
                failures.add(new ValidationError(code, "Brand (hops=1) must have ItemType = 1"));
                failedCodes.add(code);
            } else if (hops == 2 && item.getItemType() != 1) {
                failures.add(new ValidationError(code, "ProductGroup (hops=2) must have ItemType = 1"));
                failedCodes.add(code);
            } else if (hops == 3 && item.getItemType() != 2) {
                failures.add(new ValidationError(code, "Product (hops=3) must have ItemType = 2"));
                failedCodes.add(code);
            } else if (hops > 3) {
                failures.add(new ValidationError(code, "Invalid hierarchy depth. Got hops: " + hops));
                failedCodes.add(code);
            }
        }

        // ── STEP 5: PERSIST LEVEL 0 — Categories ─────────────────────────────────────
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (failedCodes.contains(code) || itemHops.get(code) == null || itemHops.get(code) != 0) continue;

            String incomingName = item.getItemName().trim();

            if (categoriesByCode.containsKey(code)) {
                // Code already exists — check if name also matches
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
        }

        // ── STEP 6: PERSIST LEVEL 1 — Brands ─────────────────────────────────────────
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (failedCodes.contains(code) || itemHops.get(code) == null || itemHops.get(code) != 1) continue;

            String incomingName = item.getItemName().trim();

            if (brandsByCode.containsKey(code)) {
                if (brandsByCode.get(code).getName().equals(incomingName)) {
                    alreadyExisting++;
                    log.info("Brand already exists: code={}, name={}", code, incomingName);
                } else {
                    String conflict = "Brand code '" + code + "' already exists with a different name: '"
                            + brandsByCode.get(code).getName() + "'";
                    failures.add(new ValidationError(code, conflict));
                    failedCodes.add(code);
                    log.warn("Brand import failed: code={}, reason={}", code, conflict);
                }
                continue;
            }

            String parentCode = item.getItemPrnt().trim();
            ProductCategory parentCat = categoriesByCode.get(parentCode);
            if (parentCat == null) {
                String reason = "Parent Category with code '" + parentCode + "' was not found or failed validation";
                failures.add(new ValidationError(code, reason));
                failedCodes.add(code);
                log.warn("Brand import failed: code={}, reason={}", code, reason);
                continue;
            }

            try {
                Brand saved = posMigrationPersister.saveBrand(
                        Brand.builder().name(incomingName).code(code).category(parentCat).build());
                brandsByCode.put(code, saved);
                log.info("Brand created: code={}, name={}", code, incomingName);
            } catch (Exception e) {
                String reason = "Failed to persist Brand: " + e.getMessage();
                failures.add(new ValidationError(code, reason));
                failedCodes.add(code);
                log.warn("Brand import failed: code={}, reason={}", code, reason);
            }
        }

        // ── STEP 7: PERSIST LEVEL 2 — ProductGroups ──────────────────────────────────
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (failedCodes.contains(code) || itemHops.get(code) == null || itemHops.get(code) != 2) continue;

            String incomingName = item.getItemName().trim();

            if (productGroupsByCode.containsKey(code)) {
                if (productGroupsByCode.get(code).getName().equals(incomingName)) {
                    alreadyExisting++;
                    log.info("ProductGroup already exists: code={}, name={}", code, incomingName);
                } else {
                    String conflict = "ProductGroup code '" + code + "' already exists with a different name: '"
                            + productGroupsByCode.get(code).getName() + "'";
                    failures.add(new ValidationError(code, conflict));
                    failedCodes.add(code);
                    log.warn("ProductGroup import failed: code={}, reason={}", code, conflict);
                }
                continue;
            }

            String parentCode = item.getItemPrnt().trim();
            Brand parentBrand = brandsByCode.get(parentCode);
            if (parentBrand == null) {
                String reason = "Parent Brand with code '" + parentCode + "' was not found or failed validation";
                failures.add(new ValidationError(code, reason));
                failedCodes.add(code);
                log.warn("ProductGroup import failed: code={}, reason={}", code, reason);
                continue;
            }

            try {
                ProductGroup saved = posMigrationPersister.saveProductGroup(
                        ProductGroup.builder().name(incomingName).code(code).brand(parentBrand).build());
                productGroupsByCode.put(code, saved);
                log.info("ProductGroup created: code={}, name={}", code, incomingName);
            } catch (Exception e) {
                String reason = "Failed to persist ProductGroup: " + e.getMessage();
                failures.add(new ValidationError(code, reason));
                failedCodes.add(code);
                log.warn("ProductGroup import failed: code={}, reason={}", code, reason);
            }
        }

        // ── STEP 8: PERSIST LEVEL 3 — Products ───────────────────────────────────────
        for (PosDataItemDto item : items) {
            String code = item.getItemCode().trim();
            if (failedCodes.contains(code) || itemHops.get(code) == null || itemHops.get(code) != 3) continue;

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
            if (parentPg == null) {
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
                existingProductCodeToName.put(code, incomingName);
                log.info("Product created: sku={}, name={}", code, incomingName);
            } catch (Exception e) {
                String reason = "Failed to persist Product: " + e.getMessage();
                failures.add(new ValidationError(code, reason));
                failedCodes.add(code);
                log.warn("Product import failed: sku={}, reason={}", code, reason);
            }
        }

        // ── RESULT ────────────────────────────────────────────────────────────────────
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
