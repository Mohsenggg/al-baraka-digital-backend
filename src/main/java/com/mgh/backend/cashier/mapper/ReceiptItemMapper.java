package com.mgh.backend.cashier.mapper;

import com.mgh.backend.cashier.dto.ReceiptItemRequest;
import com.mgh.backend.cashier.entity.ReceiptItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReceiptItemMapper {

    ReceiptItemRequest toDto(ReceiptItem item);

    List<ReceiptItemRequest> toDtoList(List<ReceiptItem> items);
}