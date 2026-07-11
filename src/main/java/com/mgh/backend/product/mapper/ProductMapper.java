package com.mgh.backend.product.mapper;

import com.mgh.backend.product.dto.response.DescAttributeDto;
import com.mgh.backend.product.dto.response.ProductBarcodeDto;
import com.mgh.backend.product.dto.response.ProductBarcodeFormDto;
import com.mgh.backend.product.dto.response.ProductDto;
import com.mgh.backend.product.dto.response.ProductListItemDto;
import com.mgh.backend.product.dto.response.ProductManageDetailDto;
import com.mgh.backend.product.dto.response.ProductSummaryDto;
import com.mgh.backend.product.dto.response.ReferenceItemDto;
import com.mgh.backend.product.dto.response.StockSummaryDto;
import com.mgh.backend.product.entity.Manufacturer;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductAttribute;
import com.mgh.backend.product.entity.ProductAttributeValue;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.StockStatus;
import com.mgh.backend.product.entity.Supplier;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    default ProductBarcodeDto toBarcodeDto(ProductBarcode barcode) {
        return ProductBarcodeDto.builder()
                .id(barcode.getId())
                .barcode(barcode.getBarcode())
                .sellingPrice(barcode.getSellingPrice())
                .buyingPrice(barcode.getBuyingPrice())
                .stock(barcode.getStock())
                .defaultBarcode(barcode.isDefault())
                .build();
    }

    default ProductBarcodeDto toBarcodeDto(ProductBarcode barcode, boolean highlightDefault) {
        ProductBarcodeDto dto = toBarcodeDto(barcode);
        if (highlightDefault) {
            dto.setDefaultBarcode(true);
        }
        return dto;
    }

    default ProductBarcodeFormDto toBarcodeFormDto(ProductBarcode barcode) {
        return ProductBarcodeFormDto.builder()
                .id(barcode.getId())
                .barcode(barcode.getBarcode())
                .sellingPrice(barcode.getSellingPrice())
                .buyingPrice(barcode.getBuyingPrice())
                .stock(barcode.getStock())
                .isDefault(barcode.isDefault())
                .build();
    }
    default DescAttributeDto toDescAttributeDto(ProductAttributeValue value) {
        return DescAttributeDto.builder()
                .id(value.getAttribute().getId())
                .name(value.getAttribute().getName())
                .value(value.getValue())
                .ui(value.getUi())
                .build();
    }

    default ReferenceItemDto toReferenceItem(ProductAttribute attribute) {
        return ReferenceItemDto.builder().id(attribute.getId()).name(attribute.getName()).build();
    }

    default ReferenceItemDto toReferenceItem(ProductCategory category) {
        return ReferenceItemDto.builder().id(category.getId()).name(category.getName()).build();
    }

    default ReferenceItemDto toReferenceItem(Manufacturer manufacturer) {
        return ReferenceItemDto.builder().id(manufacturer.getId()).name(manufacturer.getName()).build();
    }

    default ReferenceItemDto toReferenceItem(Supplier supplier) {
        return ReferenceItemDto.builder().id(supplier.getId()).name(supplier.getName()).build();
    }

    default ProductListItemDto toListItemDto(Product product) {
        return toListItemDto(product, null);
    }

    default ProductListItemDto toListItemDto(Product product, String highlightBarcode) {
        List<ProductBarcode> activeBarcodes = activeBarcodes(product);
        ProductSummaryDto summary = computeSummary(activeBarcodes);

        return ProductListItemDto.builder()
                .id(String.valueOf(product.getId()))
                .name(product.getName())
                .code(product.getCode())
                .type(product.getType())
                .status(product.getStatus())
                .category(product.getCategory() != null ? product.getCategory().getSlug() : null)
                .imageUrl(product.getImageUrl())
                .minStockLevel(product.getMinStockLevel())
                .maxStockLevel(product.getMaxStockLevel())
                .createdAt(toInstant(product.getCreatedAt()))
                .descAttributes(product.getAttributeValues().stream()
                        .map(this::toDescAttributeDto)
                        .toList())
                .barcodes(activeBarcodes.stream()
                        .map(barcode -> toBarcodeDto(
                                barcode,
                                highlightBarcode != null && highlightBarcode.equals(barcode.getBarcode())
                        ))
                        .toList())
                .summary(summary)
                .build();
    }

    default ProductManageDetailDto toManageDetailDto(Product product) {
        return ProductManageDetailDto.builder()
                .id(product.getId())
                .baseName(product.getBaseName())
                .generatedName(product.getName())
                .type(product.getType())
                .status(product.getStatus())
                .attributes(product.getAttributeValues().stream()
                        .map(this::toDescAttributeDto)
                        .toList())
                .barcodes(activeBarcodes(product).stream()
                        .map(this::toBarcodeFormDto)
                        .toList())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .manufacturerId(product.getManufacturer() != null ? product.getManufacturer().getId() : null)
                .supplierIds(product.getSuppliers().stream().map(Supplier::getId).toList())
                .build();
    }

    default ProductDto toCashierDto(Product product) {
        List<ProductBarcode> activeBarcodes = activeBarcodes(product);
        ProductBarcode defaultBarcode = findDefaultBarcode(activeBarcodes);
        int totalStock = activeBarcodes.stream().mapToInt(ProductBarcode::getStock).sum();

        return ProductDto.builder()
                .id(product.getId())
                .code(defaultBarcode != null ? defaultBarcode.getBarcode() : product.getCode())
                .name(product.getName())
                .price(defaultBarcode != null ? defaultBarcode.getSellingPrice() : BigDecimal.ZERO)
                .stock(totalStock)
                .build();
    }

    default StockSummaryDto toStockSummaryDto(Product product) {
        List<ProductBarcode> activeBarcodes = activeBarcodes(product);
        int totalStock = activeBarcodes.stream().mapToInt(ProductBarcode::getStock).sum();

        return StockSummaryDto.builder()
                .productId(product.getId())
                .totalStock(totalStock)
                .stockStatus(resolveStockStatus(totalStock, product.getMinStockLevel()))
                .minStockLevel(product.getMinStockLevel())
                .maxStockLevel(product.getMaxStockLevel())
                .barcodes(activeBarcodes.stream()
                        .map(barcode -> StockSummaryDto.StockBarcodeDto.builder()
                                .barcodeId(barcode.getId())
                                .barcode(barcode.getBarcode())
                                .stock(barcode.getStock())
                                .defaultBarcode(barcode.isDefault())
                                .build())
                        .toList())
                .build();
    }

    default ProductSummaryDto computeSummary(List<ProductBarcode> activeBarcodes) {
        if (activeBarcodes.isEmpty()) {
            return ProductSummaryDto.builder()
                    .defaultBarcodeId(null)
                    .maxSellingPrice(BigDecimal.ZERO)
                    .totalStock(0)
                    .barcodeCount(0)
                    .build();
        }

        ProductBarcode defaultBarcode = findDefaultBarcode(activeBarcodes);
        BigDecimal maxPrice = activeBarcodes.stream()
                .map(ProductBarcode::getSellingPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        int totalStock = activeBarcodes.stream().mapToInt(ProductBarcode::getStock).sum();

        return ProductSummaryDto.builder()
                .defaultBarcodeId(defaultBarcode != null ? defaultBarcode.getId() : null)
                .maxSellingPrice(maxPrice)
                .totalStock(totalStock)
                .barcodeCount(activeBarcodes.size())
                .build();
    }

    default List<ProductBarcode> activeBarcodes(Product product) {
        return product.getBarcodes().stream()
                .filter(barcode -> barcode.getDeletedAt() == null)
                .sorted(Comparator.comparing(ProductBarcode::isDefault).reversed()
                        .thenComparing(ProductBarcode::getId))
                .toList();
    }

    default ProductBarcode findDefaultBarcode(List<ProductBarcode> activeBarcodes) {
        return activeBarcodes.stream()
                .filter(ProductBarcode::isDefault)
                .findFirst()
                .orElse(activeBarcodes.isEmpty() ? null : activeBarcodes.getFirst());
    }

    default StockStatus resolveStockStatus(int totalStock, Integer minStockLevel) {
        if (totalStock == 0) {
            return StockStatus.OUTOFSTOCK;
        }
        if (minStockLevel != null && minStockLevel > 0) {
            if (totalStock <= minStockLevel) {
                return StockStatus.CRITICAL;
            }
            if (totalStock <= minStockLevel * 1.5) {
                return StockStatus.LOW;
            }
        }
        return StockStatus.HEALTHY;
    }

    default Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
