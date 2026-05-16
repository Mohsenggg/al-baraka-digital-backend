package com.mgh.backend.auth.domain.mapper;

import com.mgh.backend.auth.domain.dto.register.RegisterFormDto;
import com.mgh.backend.auth.domain.entity.RegisterForm;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RegisterFormMapper {

    RegisterFormDto toDto(RegisterForm registerForm);
}

