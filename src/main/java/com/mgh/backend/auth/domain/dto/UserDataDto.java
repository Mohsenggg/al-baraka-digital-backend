package com.mgh.backend.auth.domain.dto;

import com.mgh.backend.auth.domain.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public class UserDataDto {
        private Long id;
        private String username;
        private String email;
        private Set<Role> roles;
    }