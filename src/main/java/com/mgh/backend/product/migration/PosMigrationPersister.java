package com.mgh.backend.product.migration;

import com.mgh.backend.product.entity.Brand;
import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.ProductCategory;
import com.mgh.backend.product.entity.ProductGroup;
import com.mgh.backend.product.repository.BrandRepository;
import com.mgh.backend.product.repository.ProductBarcodeRepository;
import com.mgh.backend.product.repository.ProductCategoryRepository;
import com.mgh.backend.product.repository.ProductGroupRepository;
import com.mgh.backend.product.repository.ProductRepository;
import com.mgh.backend.product.entity.CatalogProvenance;
import com.mgh.backend.product.repository.CatalogProvenanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PosMigrationPersister {

    private final ProductCategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductGroupRepository productGroupRepository;
    private final ProductRepository productRepository;
    private final CatalogProvenanceRepository provenanceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductCategory saveCategory(ProductCategory category) {
        return categoryRepository.save(category);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ProductCategory> saveCategories(List<ProductCategory> categories) {
        return categoryRepository.saveAll(categories);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Brand saveBrand(Brand brand) {
        return brandRepository.save(brand);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Brand> saveBrands(List<Brand> brands) {
        return brandRepository.saveAll(brands);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductGroup saveProductGroup(ProductGroup pg) {
        return productGroupRepository.save(pg);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ProductGroup> saveProductGroups(List<ProductGroup> groups) {
        return productGroupRepository.saveAll(groups);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Product saveProduct(Product product, ProductBarcode pb) {
        product.getBarcodes().add(pb);
        pb.setProduct(product);
        return productRepository.save(product);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Product> saveProducts(List<Product> products) {
        return productRepository.saveAll(products);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CatalogProvenance> saveAllProvenance(List<CatalogProvenance> records) {
        return provenanceRepository.saveAll(records);
    }
}
