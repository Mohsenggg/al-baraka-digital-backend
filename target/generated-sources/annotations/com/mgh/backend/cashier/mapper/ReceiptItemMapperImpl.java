package com.mgh.backend.cashier.mapper;

import com.mgh.backend.cashier.dto.ReceiptItemRequest;
import com.mgh.backend.cashier.entity.ReceiptItem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-03T15:12:02+0300",
    comments = "version: 1.6.2, compiler: javac, environment: Java 21.0.10 (Microsoft)"
)
@Component
public class ReceiptItemMapperImpl implements ReceiptItemMapper {

    @Override
    public ReceiptItemRequest toDto(ReceiptItem item) {
        if ( item == null ) {
            return null;
        }

        ReceiptItemRequest receiptItemRequest = new ReceiptItemRequest();

        receiptItemRequest.setId( item.getId() );
        receiptItemRequest.setProductCode( item.getProductCode() );
        receiptItemRequest.setProductName( item.getProductName() );
        receiptItemRequest.setPrice( item.getPrice() );
        receiptItemRequest.setQuantity( item.getQuantity() );
        receiptItemRequest.setTotal( item.getTotal() );
        receiptItemRequest.setRemainingStock( item.getRemainingStock() );

        return receiptItemRequest;
    }

    @Override
    public List<ReceiptItemRequest> toDtoList(List<ReceiptItem> items) {
        if ( items == null ) {
            return null;
        }

        List<ReceiptItemRequest> list = new ArrayList<ReceiptItemRequest>( items.size() );
        for ( ReceiptItem receiptItem : items ) {
            list.add( toDto( receiptItem ) );
        }

        return list;
    }
}
