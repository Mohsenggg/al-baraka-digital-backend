package com.mgh.backend.auth.domain.dto;

import lombok.Data;

import java.time.Instant;


    @Data
    public class TokenExpiryDto {

        String token;

        Instant expiry;


        public TokenExpiryDto(String token, Instant expiry) {
            this.token = token;
            this.expiry = expiry;
        }

    }
