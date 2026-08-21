package com.mgh.backend.product.migration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PosDataItemDto {

    @JsonProperty("ItemCode")
    private String itemCode;

    @JsonProperty("ItemPrnt")
    private String itemPrnt;

    @JsonProperty("ItemName")
    private String itemName;

    @JsonProperty("ItemType")
    private Integer itemType;

    @JsonProperty("ItemPrice0")
    private BigDecimal itemPrice0;

    @JsonProperty("ItemPrice1")
    private BigDecimal itemPrice1;

    @JsonProperty("ItemPrice2")
    private BigDecimal itemPrice2;

    @JsonProperty("ItemPrice3")
    private BigDecimal itemPrice3;

    @JsonProperty("ItemPrice4")
    private BigDecimal itemPrice4;

    @JsonProperty("VendCode")
    private String vendCode;

    @JsonProperty("ItemMinStock")
    private Double itemMinStock;

    @JsonProperty("ItemMaxStock")
    private Double itemMaxStock;

    @JsonProperty("ItemReOrderStock")
    private Double itemReOrderStock;
}
