package com.mgh.backend.auth.domain.dto.register;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InvitationCodeResponseDto {

    private String invitationCode;
}

