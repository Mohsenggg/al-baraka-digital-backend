package com.mgh.backend.auth.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public class AuthResponseDto {
        private String token;
        private Instant expiresIn;
        private UserDataDto user;
    }