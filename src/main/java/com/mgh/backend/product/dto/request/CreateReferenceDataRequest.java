package com.mgh.backend.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateReferenceDataRequest {

    @NotBlank
    @Size(max = 255)
    private String name;
}
