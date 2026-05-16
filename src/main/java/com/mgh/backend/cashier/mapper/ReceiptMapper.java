package com.mgh.backend.cashier.mapper;

import com.mgh.backend.cashier.dto.ReceiptResponseDto;
import com.mgh.backend.cashier.entity.Receipt;
import org.mapstruct.Mapper;

@Mapper(
        componentModel = "spring",
        uses = ReceiptItemMapper.class
)
public interface ReceiptMapper {

    ReceiptResponseDto toDto(Receipt receipt);
}