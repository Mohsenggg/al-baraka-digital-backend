package com.mgh.backend.product.service.impl;

import com.mgh.backend.cashier.dto.PageResponseDto;
import com.mgh.backend.cashier.exception.BadRequestException;
import com.mgh.backend.cashier.exception.ConflictException;
import com.mgh.backend.cashier.exception.InsufficientStockException;
import com.mgh.backend.cashier.exception.ResourceNotFoundException;
import com.mgh.backend.product.dto.ProductSearchFilter;
import com.mgh.backend.product.dto.ProductSpecification;
import com.mgh.backend.product.dto.request.AddBarcodeRequest;
import com.mgh.backend.product.dto.request.CreateProductRequest;
import com.mgh.backend.product.dto.request.CreateReferenceDataRequest;
import com.mgh.backend.product.dto.request.ProductManageSaveRequest;
import com.mgh.backend.product.dto.request.ProductStatusUpdateRequest;
import com.mgh.backend.product.dto.request.UpdateProductRequest;
import com.mgh.backend.product.dto.response.ProductBarcodeDto;
import com.mgh.backend.product.dto.response.ProductDto;
import com.mgh.backend.product.dto.response.ProductListItemDto;
import com.mgh.backend.product.dto.response.ProductManageDetailDto;
import com.mgh.backend.product.dto.response.ProductReferenceDataDto;
import com.mgh.backend.product.dto.response.ProductStatusUpdateResponse;
import com.mgh.backend.product.dto.response.ReferenceItemDto;
import com.mgh.backend.product.dto.response.StockSummaryDto;
import com.mgh.backend.product.entity.Manufacturer;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductAttribute;
import com.mgh.backend.product.entity.ProductAttributeValue;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import com.mgh.backend.product.entity.Supplier;
import com.mgh.backend.product.mapper.ProductMapper;
import com.mgh.backend.product.repository.ManufacturerRepository;
import com.mgh.backend.product.repository.ProductAttributeRepository;
import com.mgh.backend.product.repository.ProductBarcodeRepository;
import com.mgh.backend.product.repository.ProductCategoryRepository;
import com.mgh.backend.product.repository.ProductRepository;
import com.mgh.backend.product.repository.SupplierRepository;
import com.mgh.backend.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductBarcodeRepository productBarcodeRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final SupplierRepository supplierRepository;
    private final ProductAttributeRepository attributeRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<ProductListItemDto> listProducts(ProductSearchFilter filter, Pageable pageable) {
        Pageable safePageable = capPageSize(pageable);
        Page<Product> page = productRepository.findAll(ProductSpecification.withFilters(filter), safePageable);
        return PageResponseDto.from(page, productMapper::toListItemDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductListItemDto> quickSearch(String query, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String safeQuery = query == null ? "" : query.trim();

        if (!StringUtils.hasText(safeQuery)) {
            return productRepository.findAllByDeletedAtIsNull().stream()
                    .limit(safeLimit)
                    .map(productMapper::toListItemDto)
                    .toList();
        }

        return productRepository
                .findTop50ByDeletedAtIsNullAndNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
                        safeQuery,
                        safeQuery
                )
                .stream()
                .limit(safeLimit)
                .map(productMapper::toListItemDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductListItemDto getById(Long id) {
        Product product = requireActiveProduct(id);
        return productMapper.toListItemDto(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductListItemDto getByCode(String code) {
        Product product = productRepository.findByCodeAndDeletedAtIsNull(code)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return productMapper.toListItemDto(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductListItemDto getByBarcode(String barcode) {
        ProductBarcode productBarcode = productBarcodeRepository.findActiveByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for barcode: " + barcode));
        return productMapper.toListItemDto(productBarcode.getProduct(), barcode);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductManageDetailDto getDetail(Long id) {
        Product product = productRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return productMapper.toManageDetailDto(product);
    }

    @Override
    public ProductManageDetailDto createDetail(ProductManageSaveRequest request) {
        validateManageRequest(request);

        Product product = Product.builder()
                .code("TEMP-" + System.nanoTime())
                .baseName(request.getBaseName().trim())
                .name(request.getName().trim())
                .type(request.getType() != null ? request.getType() : ProductType.INVENTORY)
                .status(request.getStatus() != null ? request.getStatus() : ProductStatus.DRAFT)
                .build();

        applyReferences(product, request);
        product.getBarcodes().addAll(buildBarcodes(product, request.getBarcodes(), null));
        product.getAttributeValues().addAll(buildAttributeValues(product, request.getAttributes()));
        product.getSuppliers().addAll(resolveSuppliers(request.getSupplierIds()));

        Product saved = productRepository.save(product);
        saved.setCode(generateProductCode(saved.getId()));
        saved = productRepository.save(saved);
        return productMapper.toManageDetailDto(saved);
    }

    @Override
    public ProductManageDetailDto updateDetail(Long id, ProductManageSaveRequest request) {
        Product product = productRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        validateManageRequest(request);

        product.setBaseName(request.getBaseName().trim());
        product.setName(request.getName().trim());
        if (request.getType() != null) {
            product.setType(request.getType());
        }
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        applyReferences(product, request);
        replaceBarcodes(product, request.getBarcodes(), id);
        replaceAttributeValues(product, request.getAttributes());
        replaceSuppliers(product, request.getSupplierIds());

        Product saved = productRepository.save(product);
        return productMapper.toManageDetailDto(saved);
    }

    @Override
    public void softDeleteById(Long id) {
        Product product = requireActiveProduct(id);
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    @Override
    public ProductStatusUpdateResponse updateStatus(Long id, ProductStatusUpdateRequest request) {
        Product product = requireActiveProduct(id);
        product.setStatus(request.getStatus());
        Product saved = productRepository.save(product);
        return ProductStatusUpdateResponse.builder()
                .id(saved.getId())
                .status(saved.getStatus())
                .updatedAt(saved.getUpdatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductBarcodeDto> listBarcodes(Long productId) {
        Product product = requireActiveProduct(productId);
        return productMapper.activeBarcodes(product).stream()
                .map(productMapper::toBarcodeDto)
                .toList();
    }

    @Override
    public ProductBarcodeDto addBarcode(Long productId, AddBarcodeRequest request) {
        Product product = requireActiveProduct(productId);
        String barcodeValue = request.getBarcode().trim();

        if (productBarcodeRepository.existsByBarcodeAndDeletedAtIsNull(barcodeValue)) {
            throw new ConflictException("Barcode already exists: " + barcodeValue);
        }

        if (request.isDefault()) {
            clearDefaultBarcode(product);
        }

        ProductBarcode barcode = ProductBarcode.builder()
                .product(product)
                .barcode(barcodeValue)
                .sellingPrice(request.getSellingPrice())
                .buyingPrice(request.getBuyingPrice())
                .stock(request.getStock())
                .isDefault(request.isDefault() || productMapper.activeBarcodes(product).isEmpty())
                .build();

        product.getBarcodes().add(barcode);
        productRepository.save(product);
        return productMapper.toBarcodeDto(barcode);
    }

    @Override
    public void deleteBarcode(Long productId, Long barcodeId) {
        Product product = requireActiveProduct(productId);
        List<ProductBarcode> activeBarcodes = productMapper.activeBarcodes(product);

        if (activeBarcodes.size() <= 1) {
            throw new BadRequestException("Cannot delete the only barcode");
        }

        ProductBarcode barcode = activeBarcodes.stream()
                .filter(item -> item.getId().equals(barcodeId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Barcode not found"));

        boolean wasDefault = barcode.isDefault();
        barcode.setDeletedAt(LocalDateTime.now());
        barcode.setDefault(false);

        if (wasDefault) {
            productMapper.activeBarcodes(product).stream()
                    .filter(item -> !item.getId().equals(barcodeId))
                    .findFirst()
                    .ifPresent(item -> item.setDefault(true));
        }

        productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductReferenceDataDto getReferenceData() {
        return ProductReferenceDataDto.builder()
                .attributes(attributeRepository.findAll().stream().map(productMapper::toReferenceItem).toList())
                .categories(categoryRepository.findAll().stream().map(productMapper::toReferenceItem).toList())
                .manufacturers(manufacturerRepository.findAll().stream().map(productMapper::toReferenceItem).toList())
                .suppliers(supplierRepository.findAll().stream().map(productMapper::toReferenceItem).toList())
                .build();
    }

    @Override
    public ReferenceItemDto createReferenceData(String type, CreateReferenceDataRequest request) {
        String name = request.getName().trim();
        return switch (type) {
            case "attributes" -> {
                if (attributeRepository.findByNameIgnoreCase(name).isPresent()) {
                    throw new ConflictException("Attribute already exists: " + name);
                }
                ProductAttribute saved = attributeRepository.save(ProductAttribute.builder().name(name).build());
                yield productMapper.toReferenceItem(saved);
            }
            case "categories" -> {
                if (categoryRepository.findByNameIgnoreCase(name).isPresent()) {
                    throw new ConflictException("Category already exists: " + name);
                }
                ProductCategory saved = categoryRepository.save(ProductCategory.builder()
                        .name(name)
                        .slug(slugify(name))
                        .build());
                yield productMapper.toReferenceItem(saved);
            }
            case "manufacturers" -> {
                if (manufacturerRepository.findByNameIgnoreCase(name).isPresent()) {
                    throw new ConflictException("Manufacturer already exists: " + name);
                }
                Manufacturer saved = manufacturerRepository.save(Manufacturer.builder().name(name).build());
                yield productMapper.toReferenceItem(saved);
            }
            case "suppliers" -> {
                if (supplierRepository.findByNameIgnoreCase(name).isPresent()) {
                    throw new ConflictException("Supplier already exists: " + name);
                }
                Supplier saved = supplierRepository.save(Supplier.builder().name(name).build());
                yield productMapper.toReferenceItem(saved);
            }
            default -> throw new BadRequestException("Unsupported reference data type: " + type);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public StockSummaryDto getStockSummary(Long productId) {
        Product product = requireActiveProduct(productId);
        return productMapper.toStockSummaryDto(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getAllProductsForCashier() {
        return productRepository.findAllByDeletedAtIsNull().stream()
                .map(productMapper::toCashierDto)
                .toList();
    }

    @Override
    public ProductDto createLegacyProduct(CreateProductRequest request) {
        if (productRepository.existsByCodeAndDeletedAtIsNull(request.getCode())) {
            throw new ConflictException("Product code already exists: " + request.getCode());
        }

        Product product = Product.builder()
                .code(request.getCode())
                .baseName(request.getName())
                .name(request.getName())
                .type(ProductType.INVENTORY)
                .status(ProductStatus.ACTIVE)
                .build();

        ProductBarcode barcode = ProductBarcode.builder()
                .product(product)
                .barcode(request.getCode())
                .sellingPrice(request.getPrice())
                .buyingPrice(request.getPrice())
                .stock(request.getStock())
                .isDefault(true)
                .build();
        product.getBarcodes().add(barcode);

        Product saved = productRepository.save(product);
        return productMapper.toCashierDto(saved);
    }

    @Override
    public ProductDto updateLegacyProduct(Long id, UpdateProductRequest request) {
        Product product = requireActiveProduct(id);

        if (request.getCode() != null && !request.getCode().equals(product.getCode())) {
            if (productRepository.existsByCodeAndDeletedAtIsNull(request.getCode())) {
                throw new ConflictException("Product code already exists: " + request.getCode());
            }
            product.setCode(request.getCode());
        }
        if (request.getName() != null) {
            product.setBaseName(request.getName());
            product.setName(request.getName());
        }

        ProductBarcode defaultBarcode = productMapper.findDefaultBarcode(productMapper.activeBarcodes(product));
        if (defaultBarcode != null) {
            if (request.getCode() != null) {
                defaultBarcode.setBarcode(request.getCode());
            }
            if (request.getPrice() != null) {
                defaultBarcode.setSellingPrice(request.getPrice());
                defaultBarcode.setBuyingPrice(request.getPrice());
            }
            if (request.getStock() != null) {
                defaultBarcode.setStock(request.getStock());
            }
        }

        Product saved = productRepository.save(product);
        return productMapper.toCashierDto(saved);
    }

    @Override
    public ProductDto deductStockByCode(String code, int quantity) {
        ProductBarcode lockedBarcode = productBarcodeRepository.findWithLockByBarcode(code).orElse(null);
        if (lockedBarcode != null) {
            return deductFromBarcode(lockedBarcode, quantity);
        }

        Product product = productRepository.findWithLockByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + code));

        ProductBarcode defaultBarcode = productMapper.findDefaultBarcode(productMapper.activeBarcodes(product));
        if (defaultBarcode == null) {
            throw new BadRequestException("Product has no active barcodes: " + code);
        }

        lockedBarcode = productBarcodeRepository.findWithLockByBarcode(defaultBarcode.getBarcode())
                .orElseThrow(() -> new ResourceNotFoundException("Barcode not found: " + defaultBarcode.getBarcode()));
        return deductFromBarcode(lockedBarcode, quantity);
    }

    @Override
    public void restoreStockByCode(String code, int quantity) {
        ProductBarcode lockedBarcode = productBarcodeRepository.findWithLockByBarcode(code).orElse(null);
        if (lockedBarcode != null) {
            lockedBarcode.setStock(lockedBarcode.getStock() + quantity);
            productBarcodeRepository.save(lockedBarcode);
            return;
        }

        Product product = productRepository.findWithLockByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for code: " + code));

        ProductBarcode defaultBarcode = productMapper.findDefaultBarcode(productMapper.activeBarcodes(product));
        if (defaultBarcode == null) {
            throw new BadRequestException("Product has no active barcodes: " + code);
        }

        lockedBarcode = productBarcodeRepository.findWithLockByBarcode(defaultBarcode.getBarcode())
                .orElseThrow(() -> new ResourceNotFoundException("Barcode not found: " + defaultBarcode.getBarcode()));
        lockedBarcode.setStock(lockedBarcode.getStock() + quantity);
        productBarcodeRepository.save(lockedBarcode);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getStockByCode(String code) {
        return productBarcodeRepository.findActiveByBarcode(code)
                .map(barcode -> totalStock(barcode.getProduct()))
                .or(() -> productRepository.findByCodeAndDeletedAtIsNull(code)
                        .map(this::totalStock))
                .orElse(null);
    }

    private ProductDto deductFromBarcode(ProductBarcode barcode, int quantity) {
        if (barcode.getStock() < quantity) {
            Product product = barcode.getProduct();
            throw new InsufficientStockException(
                    "Insufficient stock for product '" + product.getName() + "' (code: " + product.getCode() +
                            "): available=" + barcode.getStock() + ", requested=" + quantity);
        }

        barcode.setStock(barcode.getStock() - quantity);
        productBarcodeRepository.save(barcode);
        return productMapper.toCashierDto(barcode.getProduct());
    }

    private void validateManageRequest(ProductManageSaveRequest request) {
        if (request.getBarcodes() == null || request.getBarcodes().isEmpty()) {
            throw new BadRequestException("At least one barcode is required");
        }

        long defaultCount = request.getBarcodes().stream().filter(ProductManageSaveRequest.BarcodeInput::isDefault).count();
        if (defaultCount != 1) {
            throw new BadRequestException("Exactly one default barcode is required");
        }

        Set<String> barcodeValues = new HashSet<>();
        for (ProductManageSaveRequest.BarcodeInput barcode : request.getBarcodes()) {
            String value = barcode.getBarcode().trim();
            if (!barcodeValues.add(value)) {
                throw new BadRequestException("Duplicate barcode in request: " + value);
            }
            productBarcodeRepository.findActiveByBarcode(value).ifPresent(existing -> {
                if (barcode.getId() == null || !existing.getId().equals(barcode.getId())) {
                    throw new ConflictException("Barcode already exists: " + value);
                }
            });
        }

        if (request.getAttributes() != null) {
            Set<Long> attributeIds = new HashSet<>();
            for (ProductManageSaveRequest.AttributeInput attribute : request.getAttributes()) {
                if (!attributeIds.add(attribute.getId())) {
                    throw new BadRequestException("Duplicate attribute in request");
                }
                if (!attributeRepository.existsById(attribute.getId())) {
                    throw new ResourceNotFoundException("Attribute not found: " + attribute.getId());
                }
            }
        }

        if (request.getCategoryId() != null && !categoryRepository.existsById(request.getCategoryId())) {
            throw new ResourceNotFoundException("Category not found: " + request.getCategoryId());
        }
        if (request.getManufacturerId() != null && !manufacturerRepository.existsById(request.getManufacturerId())) {
            throw new ResourceNotFoundException("Manufacturer not found: " + request.getManufacturerId());
        }
        if (request.getSupplierIds() != null) {
            for (Long supplierId : request.getSupplierIds()) {
                if (!supplierRepository.existsById(supplierId)) {
                    throw new ResourceNotFoundException("Supplier not found: " + supplierId);
                }
            }
        }
    }

    private void applyReferences(Product product, ProductManageSaveRequest request) {
        product.setCategory(request.getCategoryId() != null
                ? categoryRepository.findById(request.getCategoryId()).orElse(null)
                : null);
        product.setManufacturer(request.getManufacturerId() != null
                ? manufacturerRepository.findById(request.getManufacturerId()).orElse(null)
                : null);
    }

    private List<ProductBarcode> buildBarcodes(
            Product product,
            List<ProductManageSaveRequest.BarcodeInput> inputs,
            Long productId
    ) {
        List<ProductBarcode> barcodes = new ArrayList<>();
        for (ProductManageSaveRequest.BarcodeInput input : inputs) {
            barcodes.add(ProductBarcode.builder()
                    .product(product)
                    .barcode(input.getBarcode().trim())
                    .sellingPrice(input.getSellingPrice())
                    .buyingPrice(input.getBuyingPrice())
                    .stock(input.getStock())
                    .isDefault(input.isDefault())
                    .build());
        }
        return barcodes;
    }

    private void replaceBarcodes(Product product, List<ProductManageSaveRequest.BarcodeInput> inputs, Long productId) {
        Set<Long> keptIds = new HashSet<>();
        for (ProductManageSaveRequest.BarcodeInput input : inputs) {
            if (input.getId() != null) {
                ProductBarcode existing = product.getBarcodes().stream()
                        .filter(barcode -> barcode.getId().equals(input.getId()))
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Barcode not found: " + input.getId()));
                existing.setBarcode(input.getBarcode().trim());
                existing.setSellingPrice(input.getSellingPrice());
                existing.setBuyingPrice(input.getBuyingPrice());
                existing.setStock(input.getStock());
                existing.setDefault(input.isDefault());
                existing.setDeletedAt(null);
                keptIds.add(existing.getId());
            } else {
                ProductBarcode created = ProductBarcode.builder()
                        .product(product)
                        .barcode(input.getBarcode().trim())
                        .sellingPrice(input.getSellingPrice())
                        .buyingPrice(input.getBuyingPrice())
                        .stock(input.getStock())
                        .isDefault(input.isDefault())
                        .build();
                product.getBarcodes().add(created);
            }
        }

        for (ProductBarcode barcode : product.getBarcodes()) {
            if (barcode.getId() != null && !keptIds.contains(barcode.getId()) && barcode.getDeletedAt() == null) {
                barcode.setDeletedAt(LocalDateTime.now());
                barcode.setDefault(false);
            }
        }
    }

    private List<ProductAttributeValue> buildAttributeValues(
            Product product,
            List<ProductManageSaveRequest.AttributeInput> inputs
    ) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }

        List<ProductAttributeValue> values = new ArrayList<>();
        for (ProductManageSaveRequest.AttributeInput input : inputs) {
            ProductAttribute attribute = attributeRepository.findById(input.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Attribute not found: " + input.getId()));
            values.add(ProductAttributeValue.builder()
                    .product(product)
                    .attribute(attribute)
                    .value(input.getValue().trim())
                    .build());
        }
        return values;
    }

    private void replaceAttributeValues(Product product, List<ProductManageSaveRequest.AttributeInput> inputs) {
        product.getAttributeValues().clear();
        product.getAttributeValues().addAll(buildAttributeValues(product, inputs));
    }

    private List<Supplier> resolveSuppliers(List<Long> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return List.of();
        }
        return supplierIds.stream()
                .map(id -> supplierRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id)))
                .toList();
    }

    private void replaceSuppliers(Product product, List<Long> supplierIds) {
        product.getSuppliers().clear();
        product.getSuppliers().addAll(resolveSuppliers(supplierIds));
    }

    private void clearDefaultBarcode(Product product) {
        productMapper.activeBarcodes(product).forEach(barcode -> barcode.setDefault(false));
    }

    private Product requireActiveProduct(Long id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private int totalStock(Product product) {
        return productMapper.activeBarcodes(product).stream().mapToInt(ProductBarcode::getStock).sum();
    }

    private String generateProductCode(Long productId) {
        int year = LocalDateTime.now().getYear();
        return "PRD-" + year + "-" + String.format("%05d", productId);
    }

    private String slugify(String value) {
        String slug = value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        if (!StringUtils.hasText(slug)) {
            slug = "item-" + Math.abs(value.hashCode());
        }
        return slug;
    }

    private Pageable capPageSize(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Sort sort = mapSort(pageable.getSort());
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }

    private Sort mapSort(Sort sort) {
        if (sort.isUnsorted()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            String property = switch (order.getProperty()) {
                case "name", "code", "createdAt", "type", "status" -> order.getProperty();
                case "maxSellingPrice", "totalStock" -> "createdAt";
                default -> "createdAt";
            };
            orders.add(new Sort.Order(order.getDirection(), property));
        }
        return Sort.by(orders);
    }
}
