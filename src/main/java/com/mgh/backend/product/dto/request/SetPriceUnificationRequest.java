package com.mgh.backend.product.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetPriceUnificationRequest {

    @NotNull(message = "isPriceUnified cannot be null")
    @JsonProperty("isPriceUnified")
    private Boolean isPriceUnified;
}
