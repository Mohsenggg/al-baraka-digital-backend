package com.mgh.backend.auth.domain.dto.register;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegistrationInitiateRequestDto {

    @NotBlank
    private String invitationCode;
}

