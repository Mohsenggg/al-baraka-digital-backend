package com.mgh.backend.auth.domain.dto.register;

import com.mgh.backend.auth.domain.enums.RegisterStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterFormDto {

    private Long id;
    private Long nodeId;
    private String username;
    private String email;
    private String phone;
    private LocalDate birthDate;
    private String gender;
    private String address;
    private RegisterStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private String approvedBy;
}

