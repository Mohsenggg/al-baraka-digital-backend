package com.mgh.backend.product.service.impl;

import com.mgh.backend.cashier.dto.PageResponseDto;
import com.mgh.backend.cashier.exception.BadRequestException;
import com.mgh.backend.cashier.exception.ConflictException;
import com.mgh.backend.cashier.exception.InsufficientStockException;
import com.mgh.backend.cashier.exception.ResourceNotFoundException;
import com.mgh.backend.product.dto.ProductSearchFilter;
import com.mgh.backend.product.dto.ProductSpecification;
import com.mgh.backend.product.dto.request.ProductManageSaveRequest;
import com.mgh.backend.product.dto.response.LightweightProductDto;
import com.mgh.backend.product.dto.response.ProductDto;
import com.mgh.backend.product.dto.response.ProductIdResponse;
import com.mgh.backend.product.dto.response.ProductListItemDto;
import com.mgh.backend.product.dto.response.ProductManageDetailDto;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductAttribute;
import com.mgh.backend.product.entity.ProductAttributeValue;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.ProductConversion;
import com.mgh.backend.product.entity.ProductMaterial;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
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
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Pageable paging = PageRequest.of(pageable.getPageNumber(), size);
        Page<Product> page = productRepository.findAll(
                ProductSpecification.withFilters(filter, pageable.getSort()),
                paging
        );
        return PageResponseDto.from(page, productMapper::toListItemDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductManageDetailDto getById(Long id) {
        Product product = productRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return productMapper.toManageDetailDto(product);
    }

    @Override
    public ProductIdResponse createProduct(ProductManageSaveRequest request) {
        validateManageRequest(null, request);

        Product product = Product.builder()
                .barcode("TEMP-" + System.nanoTime())
                .baseName(request.getBaseName().trim())
                .name(request.getName().trim())
                .type(request.getType() != null ? request.getType() : ProductType.INVENTORY)
                .status(request.getStatus() != null ? request.getStatus() : ProductStatus.DRAFT)
                .build();

        applyReferences(product, request);
        product.getBarcodes().addAll(buildBarcodes(product, request.getBarcodes()));
        product.getAttributeValues().addAll(buildAttributeValues(product, request.getAttributes()));
        product.getSuppliers().addAll(resolveSuppliers(request.getSupplierIds()));
        
        if (Boolean.TRUE.equals(request.getHasConversion())) {
            product.getConversions().addAll(buildConversions(product, request.getConversions()));
        }
        if (Boolean.TRUE.equals(request.getHasMaterials())) {
            product.getMaterials().addAll(buildMaterials(product, request.getMaterials()));
        }

        Product saved = productRepository.save(product);
        saved.setBarcode(generateProductBarcode(saved.getId()));
        saved = productRepository.save(saved);
        
        return new ProductIdResponse(saved.getId());
    }

    @Override
    public ProductIdResponse updateProduct(Long id, ProductManageSaveRequest request) {
        Product product = productRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        validateManageRequest(id, request);

        product.setBaseName(request.getBaseName().trim());
        product.setName(request.getName().trim());
        if (request.getType() != null) {
            product.setType(request.getType());
        }
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        applyReferences(product, request);
        replaceBarcodes(product, request.getBarcodes());
        replaceAttributeValues(product, request.getAttributes());
        replaceSuppliers(product, request.getSupplierIds());
        
        product.getConversions().clear();
        if (Boolean.TRUE.equals(request.getHasConversion())) {
            product.getConversions().addAll(buildConversions(product, request.getConversions()));
        }

        product.getMaterials().clear();
        if (Boolean.TRUE.equals(request.getHasMaterials())) {
            product.getMaterials().addAll(buildMaterials(product, request.getMaterials()));
        }

        Product saved = productRepository.save(product);
        return new ProductIdResponse(saved.getId());
    }

    @Override
    public void deleteProduct(Long id) {
        Product product = requireActiveProduct(id);
        LocalDateTime now = LocalDateTime.now();

        product.setDeletedAt(now);
        product.setStatus(ProductStatus.DELETED);

        for (ProductBarcode barcode : productMapper.activeBarcodes(product)) {
            barcode.setDeletedAt(now);
            barcode.setDefault(false);
        }

        productRepository.save(product);
    }

    @Override
    public ProductDto deductStockByBarcode(String barcode, double quantity) {
        ProductBarcode lockedBarcode = productBarcodeRepository.findWithLockByBarcode(barcode).orElse(null);
        if (lockedBarcode != null) {
            return deductFromBarcode(lockedBarcode, quantity);
        }

        Product product = productRepository.findWithLockByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + barcode));

        ProductBarcode defaultBarcode = productMapper.findDefaultBarcode(productMapper.activeBarcodes(product));
        if (defaultBarcode == null) {
            throw new BadRequestException("Product has no active barcodes: " + barcode);
        }

        lockedBarcode = productBarcodeRepository.findWithLockByBarcode(defaultBarcode.getBarcode())
                .orElseThrow(() -> new ResourceNotFoundException("Barcode not found: " + defaultBarcode.getBarcode()));
        return deductFromBarcode(lockedBarcode, quantity);
    }

    @Override
    public void restoreStockByBarcode(String barcode, double quantity) {
        ProductBarcode lockedBarcode = productBarcodeRepository.findWithLockByBarcode(barcode).orElse(null);
        if (lockedBarcode != null) {
            lockedBarcode.setStock(lockedBarcode.getStock() + quantity);
            productBarcodeRepository.save(lockedBarcode);
            return;
        }

        Product product = productRepository.findWithLockByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for barcode: " + barcode));

        ProductBarcode defaultBarcode = productMapper.findDefaultBarcode(productMapper.activeBarcodes(product));
        if (defaultBarcode == null) {
            throw new BadRequestException("Product has no active barcodes: " + barcode);
        }

        lockedBarcode = productBarcodeRepository.findWithLockByBarcode(defaultBarcode.getBarcode())
                .orElseThrow(() -> new ResourceNotFoundException("Barcode not found: " + defaultBarcode.getBarcode()));
        lockedBarcode.setStock(lockedBarcode.getStock() + quantity);
        productBarcodeRepository.save(lockedBarcode);
    }

    @Override
    @Transactional(readOnly = true)
    public Double getStockByBarcode(String barcode) {
        return productBarcodeRepository.findActiveByBarcode(barcode)
                .map(barcodeEntity -> totalStock(barcodeEntity.getProduct()))
                .or(() -> productRepository.findByBarcodeAndDeletedAtIsNull(barcode)
                        .map(this::totalStock))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDto getByBarcode(String barcode) {
        Product product = productBarcodeRepository.findActiveByBarcode(barcode)
                .map(ProductBarcode::getProduct)
                .or(() -> productRepository.findByBarcodeAndDeletedAtIsNull(barcode))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with barcode: " + barcode));
        
        ProductDto dto = productMapper.toCashierDto(product);
        
        if (product.getConversions() != null && !product.getConversions().isEmpty()) {
            List<ProductDto.RefillOptionDto> options = product.getConversions().stream()
                .map(conv -> ProductDto.RefillOptionDto.builder()
                    .parentProductId(conv.getParentProduct().getId())
                    .parentProductName(conv.getParentProduct().getName())
                    .parentQuantity(conv.getParentQuantity())
                    .childQuantity(conv.getChildQuantity())
                    .parentStock(totalStock(conv.getParentProduct()))
                    .isDefault(conv.isDefault())
                    .build())
                .toList();
            dto.setRefillOptions(options);
        }
        
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public com.mgh.backend.cashier.dto.RefillValidateResponse validateRefill(com.mgh.backend.cashier.dto.RefillValidateRequest request) {
        Product childProduct = productBarcodeRepository.findActiveByBarcode(request.getChildBarcode())
                .map(ProductBarcode::getProduct)
                .or(() -> productRepository.findByBarcodeAndDeletedAtIsNull(request.getChildBarcode()))
                .orElseThrow(() -> new ResourceNotFoundException("Child product not found: " + request.getChildBarcode()));
        
        ProductConversion conversion = childProduct.getConversions().stream()
                .filter(c -> c.getParentProduct().getId().equals(request.getParentProductId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No conversion found for the specified parent product"));
                
        Product parentProduct = conversion.getParentProduct();
        double parentTotalStock = totalStock(parentProduct);
        
        double requiredParentQty = (request.getRequestedChildQuantity() / conversion.getChildQuantity()) * conversion.getParentQuantity();
        
        if (parentTotalStock < requiredParentQty) {
            throw new InsufficientStockException("Insufficient stock in parent product. Required: " + requiredParentQty + ", Available: " + parentTotalStock);
        }
        
        ProductBarcode parentDefaultBarcode = productMapper.findDefaultBarcode(productMapper.activeBarcodes(parentProduct));
        ProductBarcode childDefaultBarcode = productMapper.findDefaultBarcode(productMapper.activeBarcodes(childProduct));
        
        if (parentDefaultBarcode == null || childDefaultBarcode == null) {
            throw new BadRequestException("Default barcode missing for parent or child product");
        }
        
        java.math.BigDecimal parentBuyingPrice = parentDefaultBarcode.getBuyingPrice();
        java.math.BigDecimal currentBuyingPrice = childDefaultBarcode.getBuyingPrice();
        java.math.BigDecimal currentSellingPrice = childDefaultBarcode.getSellingPrice();
        
        // ── Pricing Rule ────────────────────────────────────────────────────────────
        // New Child Buying Price = (Parent Buying Price * Parent Quantity) / Child Quantity
        java.math.BigDecimal newBuyingPrice = parentBuyingPrice
                .multiply(java.math.BigDecimal.valueOf(conversion.getParentQuantity()))
                .divide(java.math.BigDecimal.valueOf(conversion.getChildQuantity()), 2, java.math.RoundingMode.HALF_UP);
        
        // Current Markup % = (SellingPrice - BuyingPrice) / BuyingPrice × 100
        // e.g. buying=80, selling=100 → markup = 25.00%
        java.math.BigDecimal currentMarkupPercentage = java.math.BigDecimal.ZERO;
        if (currentBuyingPrice.compareTo(java.math.BigDecimal.ZERO) != 0) {
            currentMarkupPercentage = currentSellingPrice
                    .subtract(currentBuyingPrice)
                    .divide(currentBuyingPrice, 6, java.math.RoundingMode.HALF_UP)
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
        
        // Proposed Selling Price = newBuyingPrice × (1 + markup%/100)
        // e.g. newBuying=90, markup=25% → proposedSelling = 90 × 1.25 = 112.50
        java.math.BigDecimal markupMultiplier = java.math.BigDecimal.ONE
                .add(currentMarkupPercentage.divide(java.math.BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP));
        java.math.BigDecimal proposedSellingPrice = newBuyingPrice
                .multiply(markupMultiplier)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        
        // Pricing confirmation required if either buying OR selling price changes
        boolean pricingChangeRequired = currentBuyingPrice.compareTo(newBuyingPrice) != 0
                || currentSellingPrice.compareTo(proposedSellingPrice) != 0;
        // ────────────────────────────────────────────────────────────────────────────
        
        return com.mgh.backend.cashier.dto.RefillValidateResponse.builder()
                .isValid(true)
                .pricingChangeRequired(pricingChangeRequired)
                .currentBuyingPrice(currentBuyingPrice)
                .newBuyingPrice(newBuyingPrice)
                .currentSellingPrice(currentSellingPrice)
                .proposedSellingPrice(proposedSellingPrice)
                .currentMarkupPercentage(currentMarkupPercentage)
                .build();
    }

    @Override
    public ProductDto executeRefill(com.mgh.backend.cashier.dto.RefillExecuteRequest request) {
        // Use the barcode table (same as validateRefill) — the child barcode lives
        // in product_barcodes, NOT necessarily in products.barcode (the legacy field).
        ProductBarcode childDefaultBarcode = productBarcodeRepository.findWithLockByBarcode(request.getChildBarcode())
                .orElseThrow(() -> new ResourceNotFoundException("Child product not found: " + request.getChildBarcode()));

        Product childProduct = childDefaultBarcode.getProduct();
                
        ProductConversion conversion = childProduct.getConversions().stream()
                .filter(c -> c.getParentProduct().getId().equals(request.getParentProductId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No conversion found for the specified parent product"));
                
        Product parentProduct = productRepository.findDetailedById(request.getParentProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent product not found: " + request.getParentProductId()));
                
        double requiredParentQty = (request.getRequestedChildQuantity() / conversion.getChildQuantity()) * conversion.getParentQuantity();
        
        ProductBarcode parentDefaultBarcode = productMapper.findDefaultBarcode(productMapper.activeBarcodes(parentProduct));
        
        if (parentDefaultBarcode == null) {
            throw new BadRequestException("Default barcode missing for parent product");
        }
        
        // ── Re-derive pricing proposal (must exactly match what was validated) ────
        java.math.BigDecimal currentBuyingPrice  = childDefaultBarcode.getBuyingPrice();
        java.math.BigDecimal currentSellingPrice = childDefaultBarcode.getSellingPrice();
        
        java.math.BigDecimal parentBuyingPrice = parentDefaultBarcode.getBuyingPrice();
        java.math.BigDecimal newBuyingPrice = parentBuyingPrice
                .multiply(java.math.BigDecimal.valueOf(conversion.getParentQuantity()))
                .divide(java.math.BigDecimal.valueOf(conversion.getChildQuantity()), 2, java.math.RoundingMode.HALF_UP);
        
        // Re-derive current markup % from live DB prices
        java.math.BigDecimal currentMarkupPercentage = java.math.BigDecimal.ZERO;
        if (currentBuyingPrice.compareTo(java.math.BigDecimal.ZERO) != 0) {
            currentMarkupPercentage = currentSellingPrice
                    .subtract(currentBuyingPrice)
                    .divide(currentBuyingPrice, 6, java.math.RoundingMode.HALF_UP)
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
        
        java.math.BigDecimal markupMultiplier = java.math.BigDecimal.ONE
                .add(currentMarkupPercentage.divide(java.math.BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP));
        java.math.BigDecimal proposedSellingPrice = newBuyingPrice
                .multiply(markupMultiplier)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        
        boolean pricingChangeRequired = currentBuyingPrice.compareTo(newBuyingPrice) != 0
                || currentSellingPrice.compareTo(proposedSellingPrice) != 0;
        // ─────────────────────────────────────────────────────────────────────────
        
        if (pricingChangeRequired) {
            if (!request.isAcceptPricingChange()) {
                throw new BadRequestException("Pricing change is required but was not accepted");
            }
            // Verify the validated proposal hasn't gone stale (prices or markup changed since validate call)
            if (request.getExpectedNewBuyingPrice() == null
                    || request.getExpectedNewBuyingPrice().compareTo(newBuyingPrice) != 0) {
                throw new ConflictException("The pricing proposal has expired or changed (buying price). Please re-validate.");
            }
            if (request.getExpectedProposedSellingPrice() == null
                    || request.getExpectedProposedSellingPrice().compareTo(proposedSellingPrice) != 0) {
                throw new ConflictException("The pricing proposal has expired or changed (selling price). Please re-validate.");
            }
            if (request.getExpectedMarkupPercentage() == null
                    || request.getExpectedMarkupPercentage().compareTo(currentMarkupPercentage) != 0) {
                throw new ConflictException("The pricing proposal has expired or changed (markup). Please re-validate.");
            }
        }
        
        // ── Execute atomically ────────────────────────────────────────────────────
        // 1. Deduct from parent stock
        deductFromBarcode(parentDefaultBarcode, requiredParentQty);
        
        // 2. Add to child stock
        childDefaultBarcode.setStock(childDefaultBarcode.getStock() + request.getRequestedChildQuantity());
        
        // 3. Update child buying price AND selling price (markup-preserved)
        if (pricingChangeRequired && request.isAcceptPricingChange()) {
            childDefaultBarcode.setBuyingPrice(newBuyingPrice);
            childDefaultBarcode.setSellingPrice(proposedSellingPrice);
        }
        
        productBarcodeRepository.save(childDefaultBarcode);
        // ─────────────────────────────────────────────────────────────────────────
        
        return getByBarcode(request.getChildBarcode());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LightweightProductDto> getAllActiveProducts() {
        return productRepository.findAllActiveLightweightProducts(ProductStatus.ACTIVE);
    }

    private ProductDto deductFromBarcode(ProductBarcode barcode, double quantity) {
        if (barcode.getStock() < quantity) {
            Product product = barcode.getProduct();
            throw new InsufficientStockException(
                    "Insufficient stock for product '" + product.getName() + "' (barcode: " + product.getBarcode() +
                            "): available=" + barcode.getStock() + ", requested=" + quantity);
        }

        barcode.setStock(barcode.getStock() - quantity);
        productBarcodeRepository.save(barcode);
        return productMapper.toCashierDto(barcode.getProduct());
    }

    private void validateManageRequest(Long productId, ProductManageSaveRequest request) {
        if (request.getStatus() == ProductStatus.DELETED) {
            throw new BadRequestException("Use DELETE /api/products/{id} to delete a product");
        }

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
            Set<Long> supplierIds = new HashSet<>();
            for (Long supplierId : request.getSupplierIds()) {
                if (!supplierIds.add(supplierId)) {
                    throw new BadRequestException("Duplicate supplier in request: " + supplierId);
                }
                if (!supplierRepository.existsById(supplierId)) {
                    throw new ResourceNotFoundException("Supplier not found: " + supplierId);
                }
            }
        }

        if (Boolean.TRUE.equals(request.getHasConversion())) {
            validateCircularConversions(productId, request.getConversions());
        }
    }

    private void validateCircularConversions(Long currentProductId, List<ProductManageSaveRequest.ConversionInput> inputs) {
        if (inputs == null || inputs.isEmpty()) return;

        Set<Long> toCheck = new HashSet<>();
        for (ProductManageSaveRequest.ConversionInput input : inputs) {
            toCheck.add(input.getParentProductId());
        }

        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>(toCheck);

        while (!queue.isEmpty()) {
            Long parentId = queue.poll();
            if (currentProductId != null && parentId.equals(currentProductId)) {
                throw new ConflictException("Circular conversion detected! Product cannot be a conversion parent of itself.");
            }
            if (!visited.add(parentId)) {
                continue; // Already verified this path
            }
            Product parentProduct = productRepository.findByIdAndDeletedAtIsNull(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent product not found: " + parentId));

            // Walk up the conversion tree (find parents of this parent)
            for (ProductConversion conversion : parentProduct.getConversions()) {
                queue.add(conversion.getParentProduct().getId());
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

    private List<ProductBarcode> buildBarcodes(Product product, List<ProductManageSaveRequest.BarcodeInput> inputs) {
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

    private void replaceBarcodes(Product product, List<ProductManageSaveRequest.BarcodeInput> inputs) {
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

    private List<ProductAttributeValue> buildAttributeValues(Product product, List<ProductManageSaveRequest.AttributeInput> inputs) {
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

    private List<ProductConversion> buildConversions(Product product, List<ProductManageSaveRequest.ConversionInput> inputs) {
        if (inputs == null || inputs.isEmpty()) return List.of();
        
        List<ProductConversion> list = new ArrayList<>();
        for (ProductManageSaveRequest.ConversionInput input : inputs) {
            Product parentProduct = productRepository.findByIdAndDeletedAtIsNull(input.getParentProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent product not found: " + input.getParentProductId()));
            list.add(ProductConversion.builder()
                    .childProduct(product)
                    .parentProduct(parentProduct)
                    .parentQuantity(input.getParentQuantity())
                    .childQuantity(input.getChildQuantity())
                    .isDefault(input.isDefault())
                    .build());
        }
        return list;
    }

    private List<ProductMaterial> buildMaterials(Product product, List<ProductManageSaveRequest.MaterialInput> inputs) {
        if (inputs == null || inputs.isEmpty()) return List.of();
        
        List<ProductMaterial> list = new ArrayList<>();
        for (ProductManageSaveRequest.MaterialInput input : inputs) {
            Product materialProduct = productRepository.findByIdAndDeletedAtIsNull(input.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Material product not found: " + input.getProductId()));
            list.add(ProductMaterial.builder()
                    .product(product)
                    .materialProduct(materialProduct)
                    .quantity(input.getQuantity())
                    .build());
        }
        return list;
    }

    private Product requireActiveProduct(Long id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private double totalStock(Product product) {
        return productMapper.activeBarcodes(product).stream().mapToDouble(ProductBarcode::getStock).sum();
    }

    private String generateProductBarcode(Long productId) {
        int year = LocalDateTime.now().getYear();
        return "PRD-" + year + "-" + String.format("%05d", productId);
    }
}
