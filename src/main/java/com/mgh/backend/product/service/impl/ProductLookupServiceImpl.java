package com.mgh.backend.product.service.impl;

import com.mgh.backend.cashier.exception.ConflictException;
import com.mgh.backend.product.dto.request.CreateReferenceDataRequest;
import com.mgh.backend.product.dto.response.ReferenceItemDto;
import com.mgh.backend.product.entity.Manufacturer;
import com.mgh.backend.product.entity.ProductAttribute;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.Supplier;
import com.mgh.backend.product.mapper.ProductMapper;
import com.mgh.backend.product.repository.ManufacturerRepository;
import com.mgh.backend.product.repository.ProductAttributeRepository;
import com.mgh.backend.product.repository.ProductCategoryRepository;
import com.mgh.backend.product.repository.SupplierRepository;
import com.mgh.backend.product.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductLookupServiceImpl implements ProductLookupService {

    private final ProductAttributeRepository attributeRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final SupplierRepository supplierRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceItemDto> getCategories() {
        return categoryRepository.findAll().stream().map(productMapper::toReferenceItem).toList();
    }

    @Override
    @Transactional
    public ReferenceItemDto createCategory(CreateReferenceDataRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ConflictException("Category already exists: " + name);
        }
        ProductCategory saved = categoryRepository.save(ProductCategory.builder()
                .name(name)
                .slug(slugify(name))
                .build());
        return productMapper.toReferenceItem(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceItemDto> getManufacturers() {
        return manufacturerRepository.findAll().stream().map(productMapper::toReferenceItem).toList();
    }

    @Override
    @Transactional
    public ReferenceItemDto createManufacturer(CreateReferenceDataRequest request) {
        String name = request.getName().trim();
        if (manufacturerRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ConflictException("Manufacturer already exists: " + name);
        }
        Manufacturer saved = manufacturerRepository.save(Manufacturer.builder().name(name).build());
        return productMapper.toReferenceItem(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceItemDto> getSuppliers() {
        return supplierRepository.findAll().stream().map(productMapper::toReferenceItem).toList();
    }

    @Override
    @Transactional
    public ReferenceItemDto createSupplier(CreateReferenceDataRequest request) {
        String name = request.getName().trim();
        if (supplierRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ConflictException("Supplier already exists: " + name);
        }
        Supplier saved = supplierRepository.save(Supplier.builder().name(name).build());
        return productMapper.toReferenceItem(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceItemDto> getAttributes() {
        return attributeRepository.findAll().stream().map(productMapper::toReferenceItem).toList();
    }

    @Override
    @Transactional
    public ReferenceItemDto createAttribute(CreateReferenceDataRequest request) {
        String name = request.getName().trim();
        if (attributeRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ConflictException("Attribute already exists: " + name);
        }
        ProductAttribute saved = attributeRepository.save(ProductAttribute.builder().name(name).build());
        return productMapper.toReferenceItem(saved);
    }

    private String slugify(String input) {
        if (input == null) return null;
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }
}
