package com.mgh.backend.product.service.impl;

import com.mgh.backend.cashier.dto.PageResponseDto;
import com.mgh.backend.product.dto.response.BrandTreeNodeDto;
import com.mgh.backend.product.dto.response.CategoryChildNodesDto;
import com.mgh.backend.product.dto.response.CategoryTreeNodeDto;
import com.mgh.backend.product.dto.response.ProductGroupTreeNodeDto;
import com.mgh.backend.product.dto.response.ProductTreeResponseDto;
import com.mgh.backend.product.dto.response.ProductTreeStatisticsDto;
import com.mgh.backend.product.dto.response.TreeProductItemDto;
import com.mgh.backend.product.entity.Brand;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.ProductGroup;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.repository.BrandRepository;
import com.mgh.backend.product.repository.ProductCategoryRepository;
import com.mgh.backend.product.repository.ProductGroupRepository;
import com.mgh.backend.product.repository.ProductRepository;
import com.mgh.backend.product.service.ProductTreeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductTreeServiceImpl implements ProductTreeService {

    private final ProductCategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductGroupRepository productGroupRepository;
    private final ProductRepository productRepository;

    @Override
    public ProductTreeResponseDto getFullTree(
            String query,
            Long categoryId,
            Long brandId,
            String stockStatus,
            ProductStatus status,
            Boolean includeProducts
    ) {
        boolean fetchProducts = includeProducts == null || includeProducts;

        // 1. Bulk-load categories, brands, groups, and products with 0 N+1 queries.
        //    Products are loaded WITHOUT a barcodes JOIN FETCH to avoid Hibernate's SQL-level
        //    truncation bug: when you JOIN FETCH a @OneToMany collection combined with ORDER BY,
        //    Hibernate paginates the inflated Cartesian-product rows at SQL level, silently
        //    dropping products. Instead we load barcodes separately and merge in-memory.
        List<ProductCategory> allCategories = categoryRepository.findAllByOrderByNameAsc();
        List<Brand> allBrands = brandRepository.findAllWithCategory();
        List<ProductGroup> allGroups = productGroupRepository.findAllWithBrand();

        List<Product> allProducts = fetchProducts ? productRepository.findAllActiveForTree() : Collections.emptyList();

        if (fetchProducts && !allProducts.isEmpty()) {
            // Fetch all active barcodes in one query, then group them by product ID
            List<ProductBarcode> allBarcodes = productRepository.findAllActiveBarcodes();
            Map<Long, List<ProductBarcode>> barcodesByProductId = new HashMap<>();
            for (ProductBarcode pb : allBarcodes) {
                if (pb.getProduct() != null) {
                    barcodesByProductId.computeIfAbsent(pb.getProduct().getId(), k -> new ArrayList<>()).add(pb);
                }
            }
            // Attach barcodes to each product
            for (Product p : allProducts) {
                List<ProductBarcode> productBarcodes = barcodesByProductId.getOrDefault(p.getId(), Collections.emptyList());
                p.getBarcodes().clear();
                p.getBarcodes().addAll(productBarcodes);
            }
        }

        // 2. Map products into TreeProductItemDto and group by productGroupId or categoryId
        Map<Long, List<TreeProductItemDto>> productsByGroupId = new HashMap<>();
        Map<Long, List<TreeProductItemDto>> directProductsByCategoryId = new HashMap<>();
        List<TreeProductItemDto> unassignedProducts = new ArrayList<>();

        if (fetchProducts) {
            for (Product p : allProducts) {
                TreeProductItemDto item = mapToTreeProductItem(p);
                if (p.getProductGroup() != null) {
                    productsByGroupId.computeIfAbsent(p.getProductGroup().getId(), k -> new ArrayList<>()).add(item);
                } else if (p.getCategory() != null) {
                    directProductsByCategoryId.computeIfAbsent(p.getCategory().getId(), k -> new ArrayList<>()).add(item);
                } else {
                    unassignedProducts.add(item);
                }
            }
        }

        // 3. Assemble ProductGroupTreeNodeDto map
        Map<Long, ProductGroupTreeNodeDto> groupDtoMap = new HashMap<>();
        for (ProductGroup pg : allGroups) {
            Long catId = pg.getCategory() != null ? pg.getCategory().getId()
                    : (pg.getBrand() != null && pg.getBrand().getCategory() != null ? pg.getBrand().getCategory().getId() : null);
            Long bId = pg.getBrand() != null ? pg.getBrand().getId() : null;

            List<TreeProductItemDto> prods = productsByGroupId.getOrDefault(pg.getId(), new ArrayList<>());

            ProductGroupTreeNodeDto groupDto = ProductGroupTreeNodeDto.builder()
                    .id(pg.getId())
                    .code(pg.getCode())
                    .name(pg.getName())
                    .categoryId(catId)
                    .brandId(bId)
                    .productCount(prods.size())
                    .products(prods)
                    .build();

            groupDtoMap.put(pg.getId(), groupDto);
        }

        // 4. Assemble BrandTreeNodeDto map and link groups to brands
        Map<Long, BrandTreeNodeDto> brandDtoMap = new HashMap<>();
        for (Brand b : allBrands) {
            Long catId = b.getCategory() != null ? b.getCategory().getId() : null;

            BrandTreeNodeDto brandDto = BrandTreeNodeDto.builder()
                    .id(b.getId())
                    .code(b.getCode())
                    .name(b.getName())
                    .categoryId(catId)
                    .groups(new ArrayList<>())
                    .build();

            brandDtoMap.put(b.getId(), brandDto);
        }

        for (ProductGroup pg : allGroups) {
            if (pg.getBrand() != null && brandDtoMap.containsKey(pg.getBrand().getId())) {
                ProductGroupTreeNodeDto gDto = groupDtoMap.get(pg.getId());
                if (gDto != null) {
                    brandDtoMap.get(pg.getBrand().getId()).getGroups().add(gDto);
                }
            }
        }

        // Recalculate brand counts
        for (BrandTreeNodeDto bDto : brandDtoMap.values()) {
            bDto.setGroupCount(bDto.getGroups().size());
            int pCount = bDto.getGroups().stream().mapToInt(ProductGroupTreeNodeDto::getProductCount).sum();
            bDto.setProductCount(pCount);
        }

        // 5. Assemble CategoryTreeNodeDto list and link brands & direct groups
        List<CategoryTreeNodeDto> categoryList = new ArrayList<>();
        Map<Long, CategoryTreeNodeDto> categoryDtoMap = new HashMap<>();

        for (ProductCategory cat : allCategories) {
            CategoryTreeNodeDto catDto = CategoryTreeNodeDto.builder()
                    .id(cat.getId())
                    .code(cat.getCode())
                    .name(cat.getName())
                    .brands(new ArrayList<>())
                    .directGroups(new ArrayList<>())
                    .build();

            categoryList.add(catDto);
            categoryDtoMap.put(cat.getId(), catDto);
        }

        // Link brands to category
        for (Brand b : allBrands) {
            if (b.getCategory() != null && categoryDtoMap.containsKey(b.getCategory().getId())) {
                BrandTreeNodeDto bDto = brandDtoMap.get(b.getId());
                if (bDto != null) {
                    categoryDtoMap.get(b.getCategory().getId()).getBrands().add(bDto);
                }
            }
        }

        // Link direct groups (where brand is null) directly under category
        for (ProductGroup pg : allGroups) {
            if (pg.getBrand() == null) {
                Long catId = pg.getCategory() != null ? pg.getCategory().getId() : null;
                if (catId != null && categoryDtoMap.containsKey(catId)) {
                    ProductGroupTreeNodeDto gDto = groupDtoMap.get(pg.getId());
                    if (gDto != null) {
                        categoryDtoMap.get(catId).getDirectGroups().add(gDto);
                    }
                }
            }
        }

        // Link direct products (without a ProductGroup) under their category
        for (Map.Entry<Long, List<TreeProductItemDto>> entry : directProductsByCategoryId.entrySet()) {
            Long catId = entry.getKey();
            List<TreeProductItemDto> prods = entry.getValue();
            if (prods != null && !prods.isEmpty() && categoryDtoMap.containsKey(catId)) {
                CategoryTreeNodeDto catDto = categoryDtoMap.get(catId);
                ProductGroupTreeNodeDto directGroupDto = ProductGroupTreeNodeDto.builder()
                        .id(-catId)
                        .code(catDto.getCode() + "-DIRECT")
                        .name("منتجات عامة / بدون مجموعة")
                        .categoryId(catId)
                        .brandId(null)
                        .productCount(prods.size())
                        .products(prods)
                        .build();
                catDto.getDirectGroups().add(directGroupDto);
            }
        }

        // Link products without category or group under a fallback category
        if (!unassignedProducts.isEmpty()) {
            ProductGroupTreeNodeDto unassignedGroupDto = ProductGroupTreeNodeDto.builder()
                    .id(-9999L)
                    .code("OTHER-DIRECT")
                    .name("منتجات غير مصنفة")
                    .categoryId(0L)
                    .brandId(null)
                    .productCount(unassignedProducts.size())
                    .products(unassignedProducts)
                    .build();

            CategoryTreeNodeDto otherCatDto = CategoryTreeNodeDto.builder()
                    .id(0L)
                    .code("00")
                    .name("غير مصنف / أخرى")
                    .brands(new ArrayList<>())
                    .directGroups(new ArrayList<>(Collections.singletonList(unassignedGroupDto)))
                    .build();

            categoryList.add(otherCatDto);
            categoryDtoMap.put(0L, otherCatDto);
        }

        // Recalculate category counts
        for (CategoryTreeNodeDto catDto : categoryList) {
            catDto.setBrandCount(catDto.getBrands().size());
            int groupCount = catDto.getBrands().stream().mapToInt(BrandTreeNodeDto::getGroupCount).sum() + catDto.getDirectGroups().size();
            catDto.setGroupCount(groupCount);
            int pCount = catDto.getBrands().stream().mapToInt(BrandTreeNodeDto::getProductCount).sum()
                    + catDto.getDirectGroups().stream().mapToInt(ProductGroupTreeNodeDto::getProductCount).sum();
            catDto.setProductCount(pCount);
        }

        // 6. Apply filters if present
        List<CategoryTreeNodeDto> filteredList = applyFilters(
                categoryList,
                query,
                categoryId,
                brandId,
                stockStatus,
                status
        );

        // 7. Calculate overall statistics
        ProductTreeStatisticsDto stats = computeStatistics(filteredList);

        return ProductTreeResponseDto.builder()
                .tree(filteredList)
                .statistics(stats)
                .build();
    }

    private List<CategoryTreeNodeDto> applyFilters(
            List<CategoryTreeNodeDto> rawList,
            String query,
            Long categoryId,
            Long brandId,
            String stockFilter,
            ProductStatus statusFilter
    ) {
        String q = query != null ? query.trim().toLowerCase() : null;
        boolean hasQuery = q != null && !q.isEmpty();
        boolean hasCatFilter = categoryId != null;
        boolean hasBrandFilter = brandId != null;
        boolean hasStockFilter = stockFilter != null && !stockFilter.isBlank();
        boolean hasStatusFilter = statusFilter != null;

        if (!hasQuery && !hasCatFilter && !hasBrandFilter && !hasStockFilter && !hasStatusFilter) {
            return rawList;
        }

        List<CategoryTreeNodeDto> result = new ArrayList<>();

        for (CategoryTreeNodeDto cat : rawList) {
            if (hasCatFilter && !Objects.equals(cat.getId(), categoryId)) {
                continue;
            }

            boolean catMatches = !hasQuery || (cat.getName() != null && cat.getName().toLowerCase().contains(q))
                    || (cat.getCode() != null && cat.getCode().toLowerCase().contains(q));

            // Filter brands
            List<BrandTreeNodeDto> filteredBrands = new ArrayList<>();
            for (BrandTreeNodeDto brand : cat.getBrands()) {
                if (hasBrandFilter && !Objects.equals(brand.getId(), brandId)) {
                    continue;
                }

                boolean brandMatches = !hasQuery || (brand.getName() != null && brand.getName().toLowerCase().contains(q))
                        || (brand.getCode() != null && brand.getCode().toLowerCase().contains(q));

                List<ProductGroupTreeNodeDto> filteredBrandGroups = new ArrayList<>();
                for (ProductGroupTreeNodeDto group : brand.getGroups()) {
                    boolean groupMatches = !hasQuery || (group.getName() != null && group.getName().toLowerCase().contains(q))
                            || (group.getCode() != null && group.getCode().toLowerCase().contains(q));

                    List<TreeProductItemDto> matchingProds = filterProducts(group.getProducts(), q, catMatches || brandMatches || groupMatches, stockFilter, statusFilter);

                    if (catMatches || brandMatches || groupMatches || !matchingProds.isEmpty()) {
                        filteredBrandGroups.add(ProductGroupTreeNodeDto.builder()
                                .id(group.getId())
                                .code(group.getCode())
                                .name(group.getName())
                                .categoryId(group.getCategoryId())
                                .brandId(group.getBrandId())
                                .productCount(matchingProds.size())
                                .products(matchingProds)
                                .build());
                    }
                }

                if (catMatches || brandMatches || !filteredBrandGroups.isEmpty()) {
                    int pCount = filteredBrandGroups.stream().mapToInt(ProductGroupTreeNodeDto::getProductCount).sum();
                    filteredBrands.add(BrandTreeNodeDto.builder()
                            .id(brand.getId())
                            .code(brand.getCode())
                            .name(brand.getName())
                            .categoryId(brand.getCategoryId())
                            .groupCount(filteredBrandGroups.size())
                            .productCount(pCount)
                            .groups(filteredBrandGroups)
                            .build());
                }
            }

            // Filter direct groups (without brand)
            List<ProductGroupTreeNodeDto> filteredDirectGroups = new ArrayList<>();
            if (!hasBrandFilter) {
                for (ProductGroupTreeNodeDto group : cat.getDirectGroups()) {
                    boolean groupMatches = !hasQuery || (group.getName() != null && group.getName().toLowerCase().contains(q))
                            || (group.getCode() != null && group.getCode().toLowerCase().contains(q));

                    List<TreeProductItemDto> matchingProds = filterProducts(group.getProducts(), q, catMatches || groupMatches, stockFilter, statusFilter);

                    if (catMatches || groupMatches || !matchingProds.isEmpty()) {
                        filteredDirectGroups.add(ProductGroupTreeNodeDto.builder()
                                .id(group.getId())
                                .code(group.getCode())
                                .name(group.getName())
                                .categoryId(group.getCategoryId())
                                .brandId(group.getBrandId())
                                .productCount(matchingProds.size())
                                .products(matchingProds)
                                .build());
                    }
                }
            }

            if (catMatches || !filteredBrands.isEmpty() || !filteredDirectGroups.isEmpty()) {
                int totalGroups = filteredBrands.stream().mapToInt(BrandTreeNodeDto::getGroupCount).sum() + filteredDirectGroups.size();
                int totalProds = filteredBrands.stream().mapToInt(BrandTreeNodeDto::getProductCount).sum()
                        + filteredDirectGroups.stream().mapToInt(ProductGroupTreeNodeDto::getProductCount).sum();

                result.add(CategoryTreeNodeDto.builder()
                        .id(cat.getId())
                        .code(cat.getCode())
                        .name(cat.getName())
                        .brandCount(filteredBrands.size())
                        .groupCount(totalGroups)
                        .productCount(totalProds)
                        .brands(filteredBrands)
                        .directGroups(filteredDirectGroups)
                        .build());
            }
        }

        return result;
    }

    private List<TreeProductItemDto> filterProducts(
            List<TreeProductItemDto> products,
            String query,
            boolean ancestorMatched,
            String stockFilter,
            ProductStatus statusFilter
    ) {
        if (products == null || products.isEmpty()) return Collections.emptyList();

        return products.stream().filter(p -> {
            // Status check
            if (statusFilter != null && !p.getStatus().equalsIgnoreCase(statusFilter.name())) {
                return false;
            }

            // Stock check
            if (stockFilter != null && !stockFilter.isBlank()) {
                double stock = p.getStock() != null ? p.getStock() : 0.0;
                switch (stockFilter.toLowerCase()) {
                    case "in-stock" -> {
                        if (stock <= 0) return false;
                    }
                    case "outofstock" -> {
                        if (stock > 0) return false;
                    }
                    case "low" -> {
                        if (!"low".equalsIgnoreCase(p.getStockStatus())) return false;
                    }
                    case "critical" -> {
                        if (!"critical".equalsIgnoreCase(p.getStockStatus())) return false;
                    }
                    case "healthy" -> {
                        if (!"healthy".equalsIgnoreCase(p.getStockStatus())) return false;
                    }
                }
            }

            // Text search check
            if (query != null && !query.isEmpty() && !ancestorMatched) {
                boolean matchName = p.getName() != null && p.getName().toLowerCase().contains(query);
                boolean matchSku = p.getSku() != null && p.getSku().toLowerCase().contains(query);
                boolean matchVendor = p.getVendorCode() != null && p.getVendorCode().toLowerCase().contains(query);
                return matchName || matchSku || matchVendor;
            }

            return true;
        }).collect(Collectors.toList());
    }

    private ProductTreeStatisticsDto computeStatistics(List<CategoryTreeNodeDto> categories) {
        long totalCategories = categories.size();
        long totalBrands = 0;
        long totalGroups = 0;
        long totalProducts = 0;
        double totalStockUnits = 0;
        long activeProductsCount = 0;
        long lowStockProductsCount = 0;
        long outOfStockProductsCount = 0;

        for (CategoryTreeNodeDto cat : categories) {
            totalBrands += cat.getBrands().size();

            // Direct groups
            for (ProductGroupTreeNodeDto group : cat.getDirectGroups()) {
                totalGroups++;
                for (TreeProductItemDto p : group.getProducts()) {
                    totalProducts++;
                    double stock = p.getStock() != null ? p.getStock() : 0.0;
                    totalStockUnits += stock;
                    if ("active".equalsIgnoreCase(p.getStatus())) activeProductsCount++;
                    if ("low".equalsIgnoreCase(p.getStockStatus())) lowStockProductsCount++;
                    if ("outofstock".equalsIgnoreCase(p.getStockStatus()) || stock == 0) outOfStockProductsCount++;
                }
            }

            // Groups under brands
            for (BrandTreeNodeDto brand : cat.getBrands()) {
                for (ProductGroupTreeNodeDto group : brand.getGroups()) {
                    totalGroups++;
                    for (TreeProductItemDto p : group.getProducts()) {
                        totalProducts++;
                        double stock = p.getStock() != null ? p.getStock() : 0.0;
                        totalStockUnits += stock;
                        if ("active".equalsIgnoreCase(p.getStatus())) activeProductsCount++;
                        if ("low".equalsIgnoreCase(p.getStockStatus())) lowStockProductsCount++;
                        if ("outofstock".equalsIgnoreCase(p.getStockStatus()) || stock == 0) outOfStockProductsCount++;
                    }
                }
            }
        }

        return ProductTreeStatisticsDto.builder()
                .totalCategories(totalCategories)
                .totalBrands(totalBrands)
                .totalGroups(totalGroups)
                .totalProducts(totalProducts)
                .totalStockUnits(totalStockUnits)
                .activeProductsCount(activeProductsCount)
                .lowStockProductsCount(lowStockProductsCount)
                .outOfStockProductsCount(outOfStockProductsCount)
                .build();
    }

    @Override
    public ProductTreeStatisticsDto getTreeStatistics() {
        return getFullTree(null, null, null, null, null, true).getStatistics();
    }

    @Override
    public List<CategoryTreeNodeDto> getCategorySummaries() {
        return getFullTree(null, null, null, null, null, false).getTree();
    }

    @Override
    public CategoryChildNodesDto getCategoryChildNodes(Long categoryId) {
        List<Brand> brands = brandRepository.findByCategoryId(categoryId);
        List<ProductGroup> directGroups = productGroupRepository.findByCategoryIdAndBrandIsNull(categoryId);

        List<BrandTreeNodeDto> brandDtos = brands.stream().map(b -> {
            List<ProductGroup> groups = productGroupRepository.findByBrandId(b.getId());
            List<ProductGroupTreeNodeDto> groupDtos = groups.stream().map(g -> ProductGroupTreeNodeDto.builder()
                    .id(g.getId())
                    .code(g.getCode())
                    .name(g.getName())
                    .categoryId(categoryId)
                    .brandId(b.getId())
                    .productCount(0)
                    .build()).collect(Collectors.toList());

            return BrandTreeNodeDto.builder()
                    .id(b.getId())
                    .code(b.getCode())
                    .name(b.getName())
                    .categoryId(categoryId)
                    .groupCount(groupDtos.size())
                    .groups(groupDtos)
                    .build();
        }).collect(Collectors.toList());

        List<ProductGroupTreeNodeDto> directGroupDtos = directGroups.stream().map(g -> ProductGroupTreeNodeDto.builder()
                .id(g.getId())
                .code(g.getCode())
                .name(g.getName())
                .categoryId(categoryId)
                .brandId(null)
                .productCount(0)
                .build()).collect(Collectors.toList());

        return CategoryChildNodesDto.builder()
                .categoryId(categoryId)
                .brands(brandDtos)
                .directGroups(directGroupDtos)
                .build();
    }

    @Override
    public List<ProductGroupTreeNodeDto> getBrandGroups(Long brandId) {
        List<ProductGroup> groups = productGroupRepository.findByBrandId(brandId);
        return groups.stream().map(g -> ProductGroupTreeNodeDto.builder()
                .id(g.getId())
                .code(g.getCode())
                .name(g.getName())
                .categoryId(g.getCategory() != null ? g.getCategory().getId() : (g.getBrand() != null && g.getBrand().getCategory() != null ? g.getBrand().getCategory().getId() : null))
                .brandId(brandId)
                .productCount(0)
                .build()).collect(Collectors.toList());
    }

    @Override
    public PageResponseDto<TreeProductItemDto> getGroupProducts(Long groupId, Pageable pageable) {
        Page<Product> page = productRepository.findByProductGroupIdAndDeletedAtIsNull(groupId, pageable);
        return PageResponseDto.from(page, this::mapToTreeProductItem);
    }

    public TreeProductItemDto mapToTreeProductItem(Product p) {
        List<ProductBarcode> barcodes = p.getBarcodes() != null ? p.getBarcodes() : Collections.emptyList();

        ProductBarcode defaultBarcode = barcodes.stream()
                .filter(b -> b.getDeletedAt() == null && b.isDefault())
                .findFirst()
                .orElseGet(() -> barcodes.stream().filter(b -> b.getDeletedAt() == null).findFirst().orElse(null));

        BigDecimal sellingPrice = defaultBarcode != null && defaultBarcode.getSellingPrice() != null
                ? defaultBarcode.getSellingPrice() : BigDecimal.ZERO;
        BigDecimal buyingPrice = defaultBarcode != null && defaultBarcode.getBuyingPrice() != null
                ? defaultBarcode.getBuyingPrice() : BigDecimal.ZERO;

        String sku = defaultBarcode != null && defaultBarcode.getBarcode() != null
                ? defaultBarcode.getBarcode()
                : (p.getBarcode() != null ? p.getBarcode() : "");

        double totalStock = barcodes.stream()
                .filter(b -> b.getDeletedAt() == null && b.getStock() != null)
                .mapToDouble(ProductBarcode::getStock)
                .sum();

        Double minStockLevel = p.getMinStockLevel() != null ? p.getMinStockLevel() : 0.0;
        Double maxStockLevel = p.getMaxStockLevel() != null ? p.getMaxStockLevel() : 0.0;

        String stockStatusStr = calculateStockStatus(totalStock, minStockLevel);

        return TreeProductItemDto.builder()
                .id(p.getId())
                .sku(sku)
                .name(p.getName())
                .productGroupId(p.getProductGroup() != null ? p.getProductGroup().getId() : null)
                .sellingPrice(sellingPrice)
                .buyingPrice(buyingPrice)
                .price0(buyingPrice)
                .price1(sellingPrice)
                .price2(null)
                .price3(null)
                .price4(null)
                .vendorCode(p.getManufacturer() != null ? p.getManufacturer().getName() : null)
                .stock(totalStock)
                .minStockLevel(minStockLevel)
                .maxStockLevel(maxStockLevel)
                .minStock(minStockLevel)
                .maxStock(maxStockLevel)
                .stockStatus(stockStatusStr)
                .type(p.getType() != null ? p.getType().name().toLowerCase() : "inventory")
                .status(p.getStatus() != null ? p.getStatus().name().toLowerCase() : "draft")
                .build();
    }

    private String calculateStockStatus(double totalStock, double minStockLevel) {
        if (totalStock == 0) {
            return "outofstock";
        }
        if (minStockLevel > 0) {
            if (totalStock <= minStockLevel) {
                return "critical";
            }
            if (totalStock <= minStockLevel * 1.5) {
                return "low";
            }
            return "healthy";
        }
        // Fallback default thresholds if minStockLevel is not set (0)
        if (totalStock <= 5) {
            return "critical";
        }
        if (totalStock <= 15) {
            return "low";
        }
        return "healthy";
    }
}
