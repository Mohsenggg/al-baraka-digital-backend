package com.mgh.backend.auth.domain.dto.register;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegistrationInitiateResponseDto {

    private String firstName;
    private String parentName;
//    private TreeNodeStatus status;
    private String message;
}

