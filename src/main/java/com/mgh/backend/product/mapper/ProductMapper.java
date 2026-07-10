package com.mgh.backend.product.mapper;

import com.mgh.backend.product.dto.ProductDto;
import com.mgh.backend.product.entity.Product;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductDto toDto(Product product);

    List<ProductDto> toDtoList(List<Product> products);
}
