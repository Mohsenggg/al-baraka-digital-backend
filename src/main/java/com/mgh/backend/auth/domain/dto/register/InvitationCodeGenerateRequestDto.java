package com.mgh.backend.auth.domain.dto.register;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InvitationCodeGenerateRequestDto {

    @NotNull
    private Long nodeId;
}

