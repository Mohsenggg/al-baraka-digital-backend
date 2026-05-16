package com.mgh.backend.cashier.mapper;

import com.mgh.backend.cashier.dto.ProductDto;
import com.mgh.backend.cashier.entity.Product;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductDto toDto(Product product);

    List<ProductDto> toDtoList(List<Product> products);
}