package com.mgh.backend.cashier.service.impl;

import com.mgh.backend.cashier.dto.CreateProductRequest;
import com.mgh.backend.cashier.dto.ProductDto;
import com.mgh.backend.cashier.dto.UpdateProductRequest;
import com.mgh.backend.cashier.entity.Product;
import com.mgh.backend.cashier.exception.ResourceNotFoundException;
import com.mgh.backend.cashier.mapper.ProductMapper;
import com.mgh.backend.cashier.repository.ProductRepository;
import com.mgh.backend.cashier.service.ProductService;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    public List<ProductDto> searchProducts(String query) {
        String safeQuery = query == null ? "" : query.trim();

        List<Product> products = productRepository
                .findTop20ByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
                        safeQuery,
                        safeQuery
                );

        return productMapper.toDtoList(products);
    }

    // Existing search method (if need to adapt)
//    public List<ProductDto> searchProducts(String query) {
//        // Simple implementation: search by name or code containing query
//        // You can enhance with a custom repository method using LIKE queries.
//        return productRepository.findAll().stream()
//                .filter(p -> p.getCode().contains(query) || p.getName().contains(query))
//                .map(this::mapToDto)
//                .collect(Collectors.toList());
//    }

    @Override
    public List<ProductDto> getAllProducts() {

        List<Product> products = productRepository.findAll();
        return productMapper.toDtoList(products);
    }


    @Override
    public ProductDto getProductByCode(String code) {
        Product product = productRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return productMapper.toDto(product);
    }

//    public ProductDto getProductByCode(String code) {
//        Product product = productRepository.findByCode(code)
//                .orElseThrow(() -> new ProductNotFoundException("Product not found with code: " + code));
//        return mapToDto(product);
//    }

    public ProductDto createProduct(CreateProductRequest request) {
        // Check if code already exists
        if (productRepository.existsByCode(request.getCode())) {
            throw new EntityExistsException("Product code already exists: " + request.getCode());
        }

        Product product = Product.builder()
                .code(request.getCode())
                .name(request.getName())
                .price(request.getPrice())
                .stock(request.getStock())
                .build();
        Product saved = productRepository.save(product);
        return mapToDto(saved);
    }

    public ProductDto updateProduct(String code, UpdateProductRequest request) {
        Product product = productRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with code: " + code));

        // If code is being changed, check new code uniqueness (if new code != old code)
        if (request.getCode() != null && !request.getCode().equals(product.getCode())) {
            if (productRepository.existsByCode(request.getCode())) {
                throw new EntityExistsException("Product code already exists: " + request.getCode());
            }
            product.setCode(request.getCode());
        }

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getStock() != null) {
            product.setStock(request.getStock());
        }

        Product updated = productRepository.save(product);
        return mapToDto(updated);
    }





    public void deleteProductByCode(String code) {
        Product product = productRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with code: " + code));
        productRepository.delete(product);
    }



    private ProductDto mapToDto(Product product) {
        return ProductDto.builder()
                .id(product.getId())
                .code(product.getCode())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .build();
    }
}