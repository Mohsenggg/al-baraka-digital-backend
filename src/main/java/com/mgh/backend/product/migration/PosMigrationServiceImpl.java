package com.mgh.backend.product.migration;

import com.mgh.backend.product.entity.Brand;
import com.mgh.backend.product.entity.CatalogProvenance;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.ProductGroup;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import com.mgh.backend.product.migration.MigrationErrorDto.ValidationError;
import com.mgh.backend.product.repository.BrandRepository;
import com.mgh.backend.product.repository.CatalogProvenanceRepository;
import com.mgh.backend.product.repository.ProductCategoryRepository;
import com.mgh.backend.product.repository.ProductGroupRepository;
import com.mgh.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final CatalogProvenanceRepository provenanceRepository;
    private final PosMigrationPersister posMigrationPersister;

    // ── CANONICAL TAXONOMY DEFINITION (11 Canonical Categories) ───────────────────
    private static final Map<String, String> CANONICAL_CATEGORIES = new LinkedHashMap<>();
    static {
        CANONICAL_CATEGORIES.put("01", "مساحيق");
        CANONICAL_CATEGORIES.put("02", "صابون تواليت");
        CANONICAL_CATEGORIES.put("03", "ورقيات");
        CANONICAL_CATEGORIES.put("04", "مستحضرات تجميل");
        CANONICAL_CATEGORIES.put("05", "خامات");
        CANONICAL_CATEGORIES.put("06", "مبيدات");
        CANONICAL_CATEGORIES.put("07", "أدوات منزلية");
        CANONICAL_CATEGORIES.put("08", "سوائل ومنظفات");
        CANONICAL_CATEGORIES.put("09", "متنوعات");
        CANONICAL_CATEGORIES.put("011", "معطرات جو وبخور");
        CANONICAL_CATEGORIES.put("10", "تسويق وعروض");
    }

    // ── SOURCE CATEGORY TO CANONICAL CATEGORY MAPPINGS ────────────────────────────
    private static final Map<String, String> P1_TO_CANONICAL_CATEGORY = Map.of(
            "01", "01",
            "02", "02",
            "03", "05", // Legacy 03 "خامات" -> Canonical 05 "خامات"
            "04", "03", // Legacy 04 "ورقيات" -> Canonical 03 "ورقيات"
            "05", "04", // Legacy 05 "مستحضرات تجميل" -> Canonical 04 "مستحضرات تجميل"
            "06", "06",
            "07", "07",
            "08", "08",
            "09", "09",
            "10", "10"
    );

    private static final Map<String, String> P2_TO_CANONICAL_CATEGORY = Map.of(
            "01", "01",
            "02", "02",
            "03", "03",
            "04", "04",
            "05", "05",
            "06", "06",
            "07", "07",
            "08", "08",
            "09", "09",
            "011", "011"
    );

    // ── 26 REAL-WORLD BRAND IDENTITY MATCHES (Legacy Group Code -> Modern Canonical Group Code)
    private static final Map<String, String> P1_BRAND_TO_P2_BRAND = Map.ofEntries(
            Map.entry("0101", "0101"),    // اريال -> شركة اريال
            Map.entry("0102", "0104"),    // برسيل -> شركة برسيل
            Map.entry("0103", "0102"),    // اوكسي -> شركة اوكسي
            Map.entry("0104", "0105"),    // فل -> فل
            Map.entry("0105", "0106"),    // بيرل -> شركة بيرل
            Map.entry("0202", "0201"),    // ليفر -> ليفر
            Map.entry("0203", "0202"),    // حبيكو -> حبيبكو
            Map.entry("0205", "0203"),    // ديتول -> ديتول
            Map.entry("0207", "0204"),    // اوكسي -> اوكسي
            Map.entry("0402", "0303"),    // بيبي فاين -> فاين بيبي
            Map.entry("0405", "0304"),    // بيبي جوي -> بيبي جوي
            Map.entry("0404", "0302"),    // مولفكس\بي بم -> مولفكس
            Map.entry("0509", "045"),     // ايفا -> ايفا
            Map.entry("0508", "041"),     // فاتيكا -> فاتيكا
            Map.entry("05012", "0495"),   // شركة فا -> شركة فا
            Map.entry("05013", "049"),    // ماي واي -> ماي واي
            Map.entry("0513", "050300"),  // لاكتويل -> لاكتويل
            Map.entry("0505", "05017"),   // سباركل -> شركة سباركل
            Map.entry("0507", "05014"),   // هيركود -> هيركود
            Map.entry("05010", "047"),    // فيانسيه -> فيانسيه
            Map.entry("0510", "050308"),  // ليزا -> شركة ليزا
            Map.entry("0601", "0611"),    // راجون -> راجون
            Map.entry("0603", "0612"),    // كيروكس -> كيروكس
            Map.entry("0801", "086"),     // كلوركس -> كلوركس
            Map.entry("0803", "084"),     // فيبا -> فيبا
            Map.entry("0810", "087")      // فلاش -> فلاش
    );

    // ── 23 VERIFIED RAW CHEMICAL & FRAGRANCE PRODUCT MERGES (Legacy Code -> Modern Code)
    private static final Map<String, String> P1_PROD_TO_P2_PROD = Map.ofEntries(
            Map.entry("030507", "050230"),         // كيلو لون كلور
            Map.entry("050814", "04976"),          // شريط فازلين اكياس
            Map.entry("060607", "6224000855253"),  // كيلو رائحة توت فريدال
            Map.entry("030511", "31"),             // كيلو بوتاس
            Map.entry("050806", "04121"),          // شريط زيت فاتيكا اكياس
            Map.entry("030504", "050226"),         // كيلو زهرة زرقاء
            Map.entry("030407", "050213"),         // كيلو لون ابيض
            Map.entry("030806", "0905"),           // كيلو شنط صيدليه طويله
            Map.entry("0912", "04993"),            // بكر لزق عريض
            Map.entry("030807", "0906"),           // كيلو شنط صيدليه صغير
            Map.entry("060626", "6224000855239"),  // كيلو رائحة فل فريدال
            Map.entry("060627", "6224000855222"),  // كيلو رائحة عود فريدال
            Map.entry("030519", "050219"),         // كيلو مانع رغوة
            Map.entry("030522", "050208"),         // كيلو مصدف
            Map.entry("030527", "050224"),         // كيلو ايزو
            Map.entry("030529", "050220"),         // كيلو كمبرلان
            Map.entry("060633", "6223006130449"),  // كيلو رائحة مسك فريدال
            Map.entry("060634", "6223006130241"),  // كيلو رائحة ليلي فريدال
            Map.entry("030814", "0901328"),        // كيلو شنط صيدليه صغير مطبوع
            Map.entry("010620", "0108023"),        // استار كلين 5 كيلو
            Map.entry("070211", "09005"),          // جردل الاهرام
            Map.entry("030406", "3ة-00001000"),    // مجموعة صابون لمونة
            Map.entry("060403", "0607011")         // ال نور نمل
    );

    @Override
    public MigrationResultDto importPosData(List<PosDataItemDto> items) {
        if (items == null || items.isEmpty()) {
            return MigrationResultDto.builder()
                    .success(true)
                    .totalRecords(0)
                    .successfulRecords(0)
                    .createdRecords(0)
                    .disambiguatedRecords(0)
                    .mergedRecords(0)
                    .alreadyExisting(0)
                    .failedRecords(0)
                    .failures(new ArrayList<>())
                    .build();
        }

        int totalRecords = items.size();
        List<ValidationError> failures = new ArrayList<>();
        int createdRecords = 0;
        int disambiguatedRecords = 0;
        int mergedRecords = 0;
        int alreadyExisting = 0;

        // ── STEP 1: BULK PRELOAD EXISTING DATABASE STATE ─────────────────────────────
        Map<String, ProductCategory> categoriesByCode = new HashMap<>();
        Map<String, ProductCategory> categoriesByName = new HashMap<>();
        for (ProductCategory cat : categoryRepository.findAll()) {
            if (cat.getCode() != null) categoriesByCode.put(cat.getCode(), cat);
            if (cat.getName() != null) categoriesByName.put(normalize(cat.getName()), cat);
        }

        Map<String, Brand> brandsByCode = new HashMap<>();
        brandRepository.findAllWithCategory()
                .forEach(b -> brandsByCode.put(b.getCode(), b));

        Map<String, ProductGroup> productGroupsByCode = new HashMap<>();
        productGroupRepository.findAllWithBrand()
                .forEach(pg -> productGroupsByCode.put(pg.getCode(), pg));

        // Barcode -> Product name map for existing product checks
        Map<String, String> existingProductBarcodeToName = new HashMap<>();
        for (Object[] row : productRepository.findAllBarcodeAndNamePairs()) {
            if (row[0] != null) {
                existingProductBarcodeToName.put((String) row[0], row[1] != null ? (String) row[1] : "");
            }
        }

        // Preload existing provenance keys to ensure idempotency on re-run
        Set<String> existingProvenanceKeys = new HashSet<>();
        for (Object[] row : provenanceRepository.findAllExistingCatalogAndSourceIdPairs()) {
            if (row[0] != null && row[1] != null) {
                existingProvenanceKeys.add(row[0].toString() + ":" + row[1].toString());
            }
        }

        List<CatalogProvenance> provenanceToSave = new ArrayList<>();

        // ── STEP 2: PARTITION PAYLOAD INTO MODERN (PART 2) AND LEGACY (PART 1) ───────
        List<PosDataItemDto> modernItems = new ArrayList<>();
        List<PosDataItemDto> legacyItems = new ArrayList<>();

        for (PosDataItemDto item : items) {
            if (item.getItemCode() == null || item.getItemCode().trim().isEmpty()) {
                failures.add(new ValidationError(null, "ItemCode is missing or empty"));
                continue;
            }
            if (isLegacyItem(item)) {
                legacyItems.add(item);
            } else {
                modernItems.add(item);
            }
        }

        // Set of modern codes to detect cross-catalog collisions
        Set<String> modernCodes = new HashSet<>();
        for (PosDataItemDto m : modernItems) {
            modernCodes.add(m.getItemCode().trim());
        }

        // ── STEP 3: SEED CANONICAL CATEGORIES (11 Categories) ─────────────────────────
        List<ProductCategory> newCategories = new ArrayList<>();
        for (Map.Entry<String, String> entry : CANONICAL_CATEGORIES.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            if (!categoriesByCode.containsKey(code)) {
                ProductCategory cat = ProductCategory.builder().code(code).name(name).build();
                newCategories.add(cat);
            }
        }
        if (!newCategories.isEmpty()) {
            List<ProductCategory> saved = posMigrationPersister.saveCategories(newCategories);
            for (ProductCategory cat : saved) {
                categoriesByCode.put(cat.getCode(), cat);
                categoriesByName.put(normalize(cat.getName()), cat);
            }
        }

        // ── STEP 4: PROCESS MODERN CATALOG (PART 2) ──────────────────────────────────
        // (a) Intra-Part-2 Barcode Collisions Handling:
        // - 6223006126022: Oxi price revision -> Higher ID (38114, 2.0 EGP) active, lower ID (37524, 1.5 EGP) merged
        // - 305210231597: Vaseline collision -> ID 37256 active, ID 37597 isolated as domain conflict
        Set<Long> modernRecordsToSkip = new HashSet<>();
        PosDataItemDto priceRevisionOld = null;
        PosDataItemDto avonaConflict = null;

        for (PosDataItemDto m : modernItems) {
            if (m.getId() != null && m.getId() == 37524L) {
                priceRevisionOld = m;
                modernRecordsToSkip.add(37524L);
            } else if (m.getId() != null && m.getId() == 37597L) {
                avonaConflict = m;
                modernRecordsToSkip.add(37597L);
            }
        }

        if (priceRevisionOld != null) {
            mergedRecords++;
            recordProvenance(provenanceToSave, existingProvenanceKeys, "PRODUCT", 0L, "MODERN",
                    priceRevisionOld.getId(), priceRevisionOld.getItemCode().trim(), priceRevisionOld.getItemPrnt(),
                    priceRevisionOld.getItemName(), false, "PRICE_REVISION_SUPERSEDED",
                    "Price revision 1.5 EGP superseded by active 2.0 EGP record (ID 38114)");
        }

        if (avonaConflict != null) {
            failures.add(new ValidationError(avonaConflict.getItemCode().trim(),
                    "Avona Vaseline (ID 37597, ItemCode " + avonaConflict.getItemCode().trim() + ", Barcode 305210231597) shares duplicate barcode with Unilever Vaseline (ID 37256) — isolated for manual review as unresolved collision"));
        }

        // (b) Modern Grouping Nodes (ItemType = 1)
        List<PosDataItemDto> modernGroups = new ArrayList<>();
        Map<String, PosDataItemDto> modernGroupsByCode = new HashMap<>();
        for (PosDataItemDto m : modernItems) {
            if (m.getItemType() != null && m.getItemType() == 1 && !modernRecordsToSkip.contains(m.getId())) {
                modernGroups.add(m);
                modernGroupsByCode.put(m.getItemCode().trim(), m);
            }
        }

        // Compute hops for modern groups
        Map<String, Integer> modernHops = computeHops(modernGroupsByCode);
        modernGroups.sort(Comparator.comparingInt(g -> modernHops.getOrDefault(g.getItemCode().trim(), 0)));

        List<Brand> newBrands = new ArrayList<>();
        List<ProductGroup> newProductGroups = new ArrayList<>();

        for (PosDataItemDto g : modernGroups) {
            String code = g.getItemCode().trim();
            String name = g.getItemName().trim();
            String parentCode = g.getItemPrnt().trim();
            Long sourceId = g.getId() != null ? g.getId() : 0L;

            if (parentCode.equals("0")) {
                // Root category already seeded
                ProductCategory cat = categoriesByCode.get(P2_TO_CANONICAL_CATEGORY.getOrDefault(code, code));
                if (cat != null) {
                    if (existingProvenanceKeys.contains("MODERN:" + sourceId)) {
                        alreadyExisting++;
                    } else {
                        createdRecords++;
                        recordProvenance(provenanceToSave, existingProvenanceKeys, "CATEGORY", safeId(cat), "MODERN",
                                sourceId, code, parentCode, name, true, "CREATED", "Modern canonical root category");
                    }
                }
                continue;
            }

            int depth = modernHops.getOrDefault(code, 1);
            ProductCategory ancestorCategory = resolveAncestorCategory(parentCode, categoriesByCode, brandsByCode, productGroupsByCode);

            if (depth == 1) {
                // Acts as Brand
                if (brandsByCode.containsKey(code)) {
                    Brand existingBrand = brandsByCode.get(code);
                    if (verifyIdentity(existingBrand.getName(), name)) {
                        alreadyExisting++;
                    } else {
                        failures.add(new ValidationError(code, "Brand code already exists with conflicting name: " + existingBrand.getName()));
                    }
                } else {
                    Brand brand = Brand.builder().code(code).name(name).category(ancestorCategory).build();
                    newBrands.add(brand);
                    brandsByCode.put(code, brand);
                    createdRecords++;
                    recordProvenance(provenanceToSave, existingProvenanceKeys, "BRAND", 0L, "MODERN",
                            sourceId, code, parentCode, name, true, "CREATED", "Modern Brand node");
                }
            } else {
                // Acts as ProductGroup
                if (productGroupsByCode.containsKey(code)) {
                    ProductGroup existingPg = productGroupsByCode.get(code);
                    if (verifyIdentity(existingPg.getName(), name)) {
                        alreadyExisting++;
                    } else {
                        failures.add(new ValidationError(code, "Product group code already exists with conflicting name: " + existingPg.getName()));
                    }
                } else {
                    Brand parentBrand = brandsByCode.get(parentCode);
                    ProductGroup pg = ProductGroup.builder()
                            .code(code)
                            .name(name)
                            .brand(parentBrand)
                            .category(ancestorCategory)
                            .build();
                    newProductGroups.add(pg);
                    productGroupsByCode.put(code, pg);
                    createdRecords++;
                    recordProvenance(provenanceToSave, existingProvenanceKeys, "PRODUCT_GROUP", 0L, "MODERN",
                            sourceId, code, parentCode, name, true, "CREATED", "Modern ProductGroup node");
                }
            }
        }

        if (!newBrands.isEmpty()) {
            List<Brand> saved = posMigrationPersister.saveBrands(newBrands);
            saved.forEach(b -> brandsByCode.put(b.getCode(), b));
        }
        if (!newProductGroups.isEmpty()) {
            List<ProductGroup> saved = posMigrationPersister.saveProductGroups(newProductGroups);
            saved.forEach(pg -> productGroupsByCode.put(pg.getCode(), pg));
        }

        // (c) Modern Products (ItemType = 2)
        List<Product> modernProductsToSave = new ArrayList<>();
        for (PosDataItemDto item : modernItems) {
            if (item.getItemType() == null || item.getItemType() != 2 || modernRecordsToSkip.contains(item.getId())) {
                continue;
            }
            String code = item.getItemCode().trim();
            String name = item.getItemName().trim();
            String parentCode = item.getItemPrnt().trim();
            Long sourceId = item.getId() != null ? item.getId() : 0L;

            if (existingProductBarcodeToName.containsKey(code)) {
                String existingName = existingProductBarcodeToName.get(code);
                if (verifyIdentity(existingName, name)) {
                    alreadyExisting++;
                } else {
                    failures.add(new ValidationError(code, "Barcode already exists with different name: " + existingName));
                }
                continue;
            }

            ProductGroup parentPg = productGroupsByCode.get(parentCode);
            ProductCategory parentCat = parentPg != null ? parentPg.getCategory() : categoriesByCode.get(parentCode);

            Product product = buildProduct(code, name, parentCat, parentPg, null);
            ProductBarcode pb = buildBarcode(code, item.getItemPrice0(), item.getItemPrice1(), product);
            product.getBarcodes().add(pb);

            modernProductsToSave.add(product);
            existingProductBarcodeToName.put(code, name);
            createdRecords++;

            recordProvenance(provenanceToSave, existingProvenanceKeys, "PRODUCT", 0L, "MODERN",
                    sourceId, code, parentCode, name, true, "CREATED", "Active modern retail product");
        }

        if (!modernProductsToSave.isEmpty()) {
            posMigrationPersister.saveProducts(modernProductsToSave);
        }

        // ── STEP 5: PROCESS LEGACY CATALOG (PART 1) ──────────────────────────────────
        // (a) Legacy Root Categories
        for (PosDataItemDto item : legacyItems) {
            if (item.getItemType() != null && item.getItemType() == 1 && item.getItemPrnt().trim().equals("0")) {
                String code = item.getItemCode().trim();
                String name = item.getItemName().trim();
                String canonicalCode = P1_TO_CANONICAL_CATEGORY.getOrDefault(code, code);
                Long sourceId = item.getId() != null ? item.getId() : 0L;

                if (code.equals("10")) {
                    // Category 10 is created as canonical
                    ProductCategory cat10 = categoriesByCode.get("10");
                    if (existingProvenanceKeys.contains("LEGACY:" + sourceId)) {
                        alreadyExisting++;
                    } else {
                        createdRecords++;
                        recordProvenance(provenanceToSave, existingProvenanceKeys, "CATEGORY", safeId(cat10), "LEGACY",
                                sourceId, code, "0", name, true, "CREATED", "Legacy promo canonical category");
                    }
                } else {
                    // Merged into canonical category (9 categories: 01-09)
                    ProductCategory canonicalCat = categoriesByCode.get(canonicalCode);
                    mergedRecords++;
                    recordProvenance(provenanceToSave, existingProvenanceKeys, "CATEGORY",
                            safeId(canonicalCat), "LEGACY",
                            sourceId, code, "0", name, true, "MERGED_CANONICAL",
                            "Merged into canonical Category " + canonicalCode + " (" + CANONICAL_CATEGORIES.get(canonicalCode) + ")");
                }
            }
        }

        // (b) Legacy Grouping Nodes (ItemType = 1, depth >= 1)
        List<Brand> legacyBrandsToSave = new ArrayList<>();
        List<ProductGroup> legacyGroupsToSave = new ArrayList<>();

        for (PosDataItemDto item : legacyItems) {
            if (item.getItemType() == null || item.getItemType() != 1 || item.getItemPrnt().trim().equals("0")) {
                continue;
            }
            String code = item.getItemCode().trim();
            String name = item.getItemName().trim();
            String parentCode = item.getItemPrnt().trim();
            Long sourceId = item.getId() != null ? item.getId() : 0L;

            String canonicalCatCode = P1_TO_CANONICAL_CATEGORY.getOrDefault(parentCode, "09");
            ProductCategory canonicalCategory = categoriesByCode.get(canonicalCatCode);

            if (P1_BRAND_TO_P2_BRAND.containsKey(code)) {
                // Real-world Brand match: merge into canonical brand (26 brands)
                String targetBrandCode = P1_BRAND_TO_P2_BRAND.get(code);
                Brand targetBrand = brandsByCode.get(targetBrandCode);

                mergedRecords++;
                recordProvenance(provenanceToSave, existingProvenanceKeys, "BRAND",
                        safeId(targetBrand), "LEGACY",
                        sourceId, code, parentCode, name, true, "MERGED_CANONICAL",
                        "Merged into canonical Brand " + targetBrandCode + " (" + (targetBrand != null ? targetBrand.getName() : "") + ")");
            } else {
                // Unique to legacy: create as legacy group
                String assignedCode = code;
                if (modernCodes.contains(code)) {
                    assignedCode = "LEG-" + code;
                }

                if (brandsByCode.containsKey(assignedCode) || productGroupsByCode.containsKey(assignedCode)) {
                    ProductGroup existingPg = productGroupsByCode.get(assignedCode);
                    String existingName = existingPg != null ? existingPg.getName() :
                            (brandsByCode.get(assignedCode) != null ? brandsByCode.get(assignedCode).getName() : "");
                    if (verifyIdentity(existingName, name)) {
                        alreadyExisting++;
                    } else {
                        failures.add(new ValidationError(assignedCode, "Group code already exists with conflicting name: " + existingName));
                    }
                } else {
                    ProductGroup pg = ProductGroup.builder()
                            .code(assignedCode)
                            .name(name)
                            .category(canonicalCategory)
                            .build();
                    legacyGroupsToSave.add(pg);
                    productGroupsByCode.put(code, pg); // Index by original code for child linking
                    productGroupsByCode.put(assignedCode, pg);
                    createdRecords++;

                    recordProvenance(provenanceToSave, existingProvenanceKeys, "PRODUCT_GROUP", 0L, "LEGACY",
                            sourceId, code, parentCode, name, true,
                            assignedCode.startsWith("LEG-") ? "CODE_DISAMBIGUATED" : "CREATED",
                            "Legacy-unique ProductGroup under Category " + canonicalCatCode);
                }
            }
        }

        if (!legacyGroupsToSave.isEmpty()) {
            posMigrationPersister.saveProductGroups(legacyGroupsToSave);
        }

        // (c) Legacy Products (ItemType = 2)
        List<Product> legacyProductsToSave = new ArrayList<>();

        for (PosDataItemDto item : legacyItems) {
            if (item.getItemType() == null || item.getItemType() != 2) {
                continue;
            }
            String code = item.getItemCode().trim();
            String name = item.getItemName().trim();
            String parentCode = item.getItemPrnt().trim();
            Long sourceId = item.getId() != null ? item.getId() : 0L;

            // 1. Check verified raw chemical / bulk fragrance merge (23 products)
            if (P1_PROD_TO_P2_PROD.containsKey(code)) {
                String targetModernCode = P1_PROD_TO_P2_PROD.get(code);
                mergedRecords++;
                recordProvenance(provenanceToSave, existingProvenanceKeys, "PRODUCT", 0L, "LEGACY",
                        sourceId, code, parentCode, name, true, "MERGED_PRODUCT_IDENTITY",
                        "Merged into modern active product " + targetModernCode);
                continue;
            }

            // 2. Check if already exists in DB
            String targetBarcode = code;
            String legacyCode = null;
            boolean isDisambiguated = false;

            if (modernCodes.contains(code)) {
                targetBarcode = "LEG-" + code;
                legacyCode = code;
                isDisambiguated = true;
            }

            if (existingProductBarcodeToName.containsKey(targetBarcode)) {
                String existingName = existingProductBarcodeToName.get(targetBarcode);
                if (verifyIdentity(existingName, name)) {
                    alreadyExisting++;
                } else {
                    failures.add(new ValidationError(targetBarcode, "Barcode already exists with different name: " + existingName));
                }
                continue;
            }

            // Resolve parent hierarchy
            ProductGroup parentPg = productGroupsByCode.get(parentCode);
            if (parentPg == null && P1_BRAND_TO_P2_BRAND.containsKey(parentCode)) {
                String modernBrandCode = P1_BRAND_TO_P2_BRAND.get(parentCode);
                parentPg = productGroupsByCode.get(modernBrandCode);
            }
            ProductCategory parentCat = parentPg != null ? parentPg.getCategory() :
                    categoriesByCode.get(P1_TO_CANONICAL_CATEGORY.getOrDefault(parentCode, "09"));

            Product product = buildProduct(targetBarcode, name, parentCat, parentPg, legacyCode);
            ProductBarcode pb = buildBarcode(targetBarcode, item.getItemPrice0(), item.getItemPrice1(), product);
            product.getBarcodes().add(pb);

            legacyProductsToSave.add(product);
            existingProductBarcodeToName.put(targetBarcode, name);
            createdRecords++;
            if (isDisambiguated) {
                disambiguatedRecords++;
            }

            recordProvenance(provenanceToSave, existingProvenanceKeys, "PRODUCT", 0L, "LEGACY",
                    sourceId, code, parentCode, name, true,
                    isDisambiguated ? "CODE_DISAMBIGUATED" : "CREATED",
                    isDisambiguated ? "Barcode disambiguated with LEG- prefix due to collision with modern code" : "Legacy wholesale product");
        }

        if (!legacyProductsToSave.isEmpty()) {
            posMigrationPersister.saveProducts(legacyProductsToSave);
        }

        // ── STEP 6: PERSIST CATALOG PROVENANCE AUDIT ROWS ─────────────────────────────
        if (!provenanceToSave.isEmpty()) {
            posMigrationPersister.saveAllProvenance(provenanceToSave);
        }

        // ── STEP 7: BUILD COMPREHENSIVE RESULT SUMMARY ────────────────────────────────
        int failedRecords = failures.size();
        int successfulRecords = createdRecords + mergedRecords + alreadyExisting;

        log.info("Migration Summary: total={}, created={} (disambiguated={}), merged={}, alreadyExisting={}, failed={}",
                totalRecords, createdRecords, disambiguatedRecords, mergedRecords, alreadyExisting, failedRecords);

        return MigrationResultDto.builder()
                .success(true)
                .totalRecords(totalRecords)
                .successfulRecords(successfulRecords)
                .createdRecords(createdRecords)
                .disambiguatedRecords(disambiguatedRecords)
                .mergedRecords(mergedRecords)
                .alreadyExisting(alreadyExisting)
                .failedRecords(failedRecords)
                .failures(failures)
                .build();
    }

    // ── HELPER METHODS ────────────────────────────────────────────────────────────

    private boolean isLegacyItem(PosDataItemDto item) {
        if (item.getId() != null) {
            return item.getId() <= 30000L;
        }
        // Fallback heuristic if ID is missing: legacy codes are short (< 8 chars) under old roots
        String prnt = item.getItemPrnt() != null ? item.getItemPrnt().trim() : "";
        return prnt.equals("10") || (item.getItemCode() != null && item.getItemCode().trim().length() <= 6);
    }

    private Product buildProduct(String barcode, String name, ProductCategory cat, ProductGroup pg, String legacyCode) {
        return Product.builder()
                .barcode(barcode)
                .legacyCode(legacyCode)
                .baseName(name)
                .name(name)
                .type(ProductType.INVENTORY)
                .status(ProductStatus.ACTIVE)
                .productGroup(pg)
                .category(cat)
                .minStockLevel(0.0)
                .maxStockLevel(0.0)
                .barcodes(new ArrayList<>())
                .build();
    }

    private ProductBarcode buildBarcode(String barcode, BigDecimal buyingPrice, BigDecimal sellingPrice, Product product) {
        return ProductBarcode.builder()
                .barcode(barcode)
                .buyingPrice(buyingPrice != null ? buyingPrice : BigDecimal.ZERO)
                .sellingPrice(sellingPrice != null ? sellingPrice : BigDecimal.ZERO)
                .stock(0.0)
                .isDefault(true)
                .product(product)
                .build();
    }

    private void recordProvenance(List<CatalogProvenance> list, Set<String> existingKeys,
                                  String entityType, Long entityId, String catalog,
                                  Long sourceId, String code, String parentCode,
                                  String name, boolean isCanonical, String status, String notes) {
        String key = catalog + ":" + sourceId;
        if (existingKeys.contains(key)) {
            return; // Idempotent: never insert duplicate provenance
        }
        existingKeys.add(key);

        CatalogProvenance cp = CatalogProvenance.builder()
                .entityType(entityType)
                .entityId(entityId != null ? entityId : 0L)
                .sourceCatalog(catalog)
                .sourceId(sourceId)
                .sourceCode(code)
                .sourceParentCode(parentCode)
                .sourceName(name)
                .isCanonical(isCanonical)
                .mappingStatus(status)
                .notes(notes)
                .createdAt(LocalDateTime.now())
                .build();
        list.add(cp);
    }

    private ProductCategory resolveAncestorCategory(String parentCode,
                                                    Map<String, ProductCategory> categories,
                                                    Map<String, Brand> brands,
                                                    Map<String, ProductGroup> groups) {
        if (categories.containsKey(parentCode)) {
            return categories.get(parentCode);
        }
        if (brands.containsKey(parentCode)) {
            return brands.get(parentCode).getCategory();
        }
        if (groups.containsKey(parentCode)) {
            ProductGroup pg = groups.get(parentCode);
            return pg.getCategory() != null ? pg.getCategory() : (pg.getBrand() != null ? pg.getBrand().getCategory() : null);
        }
        return null;
    }

    private Long safeId(ProductCategory cat) {
        return (cat != null && cat.getId() != null) ? cat.getId() : 0L;
    }

    private Long safeId(Brand b) {
        return (b != null && b.getId() != null) ? b.getId() : 0L;
    }

    private Long safeId(ProductGroup pg) {
        return (pg != null && pg.getId() != null) ? pg.getId() : 0L;
    }

    private Map<String, Integer> computeHops(Map<String, PosDataItemDto> itemsByCode) {
        Map<String, Integer> hopsMap = new HashMap<>();
        for (String code : itemsByCode.keySet()) {
            int hops = 0;
            String curr = code;
            Set<String> visited = new HashSet<>();
            while (curr != null && !curr.equals("0")) {
                if (visited.contains(curr)) break;
                visited.add(curr);
                PosDataItemDto next = itemsByCode.get(curr);
                if (next == null || next.getItemPrnt() == null) break;
                String parent = next.getItemPrnt().trim();
                if (parent.equals("0")) {
                    break;
                }
                hops++;
                curr = parent;
            }
            hopsMap.put(code, hops);
        }
        return hopsMap;
    }

    private boolean verifyIdentity(String existingName, String incomingName) {
        if (existingName == null || incomingName == null) return false;
        String n1 = normalize(existingName);
        String n2 = normalize(incomingName);
        return n1.equals(n2) || n1.startsWith(n2) || n2.startsWith(n1);
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.trim()
                .replace("أ", "ا")
                .replace("إ", "ا")
                .replace("آ", "ا")
                .replace("ة", "ه")
                .replace("ى", "ي")
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }
}
