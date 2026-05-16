package com.mgh.backend.cashier.mapper;

import com.mgh.backend.cashier.dto.ProductDto;
import com.mgh.backend.cashier.entity.Product;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-16T14:51:00+0300",
    comments = "version: 1.6.2, compiler: javac, environment: Java 21.0.10 (Microsoft)"
)
@Component
public class ProductMapperImpl implements ProductMapper {

    @Override
    public ProductDto toDto(Product product) {
        if ( product == null ) {
            return null;
        }

        ProductDto.ProductDtoBuilder productDto = ProductDto.builder();

        productDto.id( product.getId() );
        productDto.code( product.getCode() );
        productDto.name( product.getName() );
        productDto.price( product.getPrice() );
        productDto.stock( product.getStock() );

        return productDto.build();
    }

    @Override
    public List<ProductDto> toDtoList(List<Product> products) {
        if ( products == null ) {
            return null;
        }

        List<ProductDto> list = new ArrayList<ProductDto>( products.size() );
        for ( Product product : products ) {
            list.add( toDto( product ) );
        }

        return list;
    }
}
