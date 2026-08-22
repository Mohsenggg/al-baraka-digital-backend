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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PosMigrationPersister {

    private final ProductCategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductGroupRepository productGroupRepository;
    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductCategory saveCategory(ProductCategory category) {
        return categoryRepository.save(category);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Brand saveBrand(Brand brand) {
        return brandRepository.save(brand);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductGroup saveProductGroup(ProductGroup pg) {
        return productGroupRepository.save(pg);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Product saveProduct(Product product, ProductBarcode pb) {
        product.getBarcodes().add(pb);
        pb.setProduct(product);
        return productRepository.save(product);
    }
}
