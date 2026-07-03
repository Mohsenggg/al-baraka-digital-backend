package com.mgh.backend.cashier.mapper;

import com.mgh.backend.cashier.dto.ReceiptItemResponse;
import com.mgh.backend.cashier.dto.ReceiptResponseDto;
import com.mgh.backend.cashier.entity.Receipt;
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
public class ReceiptMapperImpl implements ReceiptMapper {

    @Override
    public ReceiptResponseDto toDto(Receipt receipt) {
        if ( receipt == null ) {
            return null;
        }

        ReceiptResponseDto.ReceiptResponseDtoBuilder receiptResponseDto = ReceiptResponseDto.builder();

        receiptResponseDto.id( receipt.getId() );
        receiptResponseDto.receiptNumber( receipt.getReceiptNumber() );
        receiptResponseDto.receiptDate( receipt.getReceiptDate() );
        receiptResponseDto.paymentMethod( receipt.getPaymentMethod() );
        receiptResponseDto.receiptType( receipt.getReceiptType() );
        receiptResponseDto.customerName( receipt.getCustomerName() );
        receiptResponseDto.totalAmount( receipt.getTotalAmount() );
        receiptResponseDto.totalQuantity( receipt.getTotalQuantity() );
        receiptResponseDto.totalItems( receipt.getTotalItems() );
        receiptResponseDto.status( receipt.getStatus() );
        receiptResponseDto.createdAt( receipt.getCreatedAt() );
        receiptResponseDto.updatedAt( receipt.getUpdatedAt() );
        receiptResponseDto.items( receiptItemListToReceiptItemResponseList( receipt.getItems() ) );

        return receiptResponseDto.build();
    }

    protected ReceiptItemResponse receiptItemToReceiptItemResponse(ReceiptItem receiptItem) {
        if ( receiptItem == null ) {
            return null;
        }

        ReceiptItemResponse.ReceiptItemResponseBuilder receiptItemResponse = ReceiptItemResponse.builder();

        receiptItemResponse.productCode( receiptItem.getProductCode() );
        receiptItemResponse.productName( receiptItem.getProductName() );
        receiptItemResponse.quantity( receiptItem.getQuantity() );
        receiptItemResponse.remainingStock( receiptItem.getRemainingStock() );

        return receiptItemResponse.build();
    }

    protected List<ReceiptItemResponse> receiptItemListToReceiptItemResponseList(List<ReceiptItem> list) {
        if ( list == null ) {
            return null;
        }

        List<ReceiptItemResponse> list1 = new ArrayList<ReceiptItemResponse>( list.size() );
        for ( ReceiptItem receiptItem : list ) {
            list1.add( receiptItemToReceiptItemResponse( receiptItem ) );
        }

        return list1;
    }
}
