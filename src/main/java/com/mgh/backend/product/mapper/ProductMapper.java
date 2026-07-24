package com.mgh.backend.product.mapper;

import com.mgh.backend.product.dto.response.DescAttributeDto;
import com.mgh.backend.product.dto.response.ProductBarcodeDto;
import com.mgh.backend.product.dto.response.ProductBarcodeFormDto;
import com.mgh.backend.product.dto.response.ProductDto;
import com.mgh.backend.product.dto.response.ProductListItemDto;
import com.mgh.backend.product.dto.response.ProductManageDetailDto;
import com.mgh.backend.product.dto.response.ReferenceItemDto;
import com.mgh.backend.product.entity.Manufacturer;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductAttribute;
import com.mgh.backend.product.entity.ProductAttributeValue;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.ProductConversion;
import com.mgh.backend.product.entity.ProductMaterial;
import com.mgh.backend.product.entity.Supplier;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring")
public interface    ProductMapper {

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
        List<ProductBarcode> activeBarcodes = activeBarcodes(product);
        ProductBarcode defaultBarcode = findDefaultBarcode(activeBarcodes);
        int totalStock = activeBarcodes.stream().mapToInt(ProductBarcode::getStock).sum();

        return ProductListItemDto.builder()
                .id(product.getId())
                .name(product.getName())
                .barcode(product.getBarcode())
                .category(product.getCategory() != null ? product.getCategory().getName() : null)
                .manufacturer(product.getManufacturer() != null ? product.getManufacturer().getName() : null)
                .sellingPrice(defaultBarcode != null ? defaultBarcode.getSellingPrice() : BigDecimal.ZERO)
                .stock(totalStock)
                .status(product.getStatus() != null ? product.getStatus().getValue() : null)
                .build();
    }

    default ProductManageDetailDto toManageDetailDto(Product product) {
        boolean hasConversions = product.getConversions() != null && !product.getConversions().isEmpty();
        boolean hasMaterials = product.getMaterials() != null && !product.getMaterials().isEmpty();

        return ProductManageDetailDto.builder()
                .id(product.getId())
                .baseName(product.getBaseName())
                .name(product.getName())
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
                .hasConversion(hasConversions)
                .conversions(hasConversions ? product.getConversions().stream().map(this::toConversionDto).toList() : List.of())
                .hasMaterials(hasMaterials)
                .materials(hasMaterials ? product.getMaterials().stream().map(this::toMaterialDto).toList() : List.of())
                .build();
    }

    default ProductManageDetailDto.ConversionDto toConversionDto(ProductConversion conversion) {
        return ProductManageDetailDto.ConversionDto.builder()
                .parentProductId(conversion.getParentProduct().getId())
                .parentProductName(conversion.getParentProduct().getName())
                .parentQuantity(conversion.getParentQuantity())
                .childQuantity(conversion.getChildQuantity())
                .build();
    }

    default ProductManageDetailDto.MaterialDto toMaterialDto(ProductMaterial material) {
        return ProductManageDetailDto.MaterialDto.builder()
                .productId(material.getMaterialProduct().getId())
                .quantity(material.getQuantity())
                .build();
    }

    default ProductDto toCashierDto(Product product) {
        List<ProductBarcode> activeBarcodes = activeBarcodes(product);
        ProductBarcode defaultBarcode = findDefaultBarcode(activeBarcodes);
        int totalStock = activeBarcodes.stream().mapToInt(ProductBarcode::getStock).sum();

        return ProductDto.builder()
                .id(product.getId())
                .barcode(defaultBarcode != null ? defaultBarcode.getBarcode() : product.getBarcode())
                .name(product.getName())
                .price(defaultBarcode != null ? defaultBarcode.getSellingPrice() : BigDecimal.ZERO)
                .stock(totalStock)
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
}
