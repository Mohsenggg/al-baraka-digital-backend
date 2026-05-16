package com.mgh.backend.auth.domain.mapper;

import com.mgh.backend.auth.domain.dto.register.RegisterFormDto;
import com.mgh.backend.auth.domain.entity.RegisterForm;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-16T14:50:59+0300",
    comments = "version: 1.6.2, compiler: javac, environment: Java 21.0.10 (Microsoft)"
)
@Component
public class RegisterFormMapperImpl implements RegisterFormMapper {

    @Override
    public RegisterFormDto toDto(RegisterForm registerForm) {
        if ( registerForm == null ) {
            return null;
        }

        RegisterFormDto.RegisterFormDtoBuilder registerFormDto = RegisterFormDto.builder();

        registerFormDto.id( registerForm.getId() );
        registerFormDto.nodeId( registerForm.getNodeId() );
        registerFormDto.username( registerForm.getUsername() );
        registerFormDto.email( registerForm.getEmail() );
        registerFormDto.phone( registerForm.getPhone() );
        registerFormDto.birthDate( registerForm.getBirthDate() );
        registerFormDto.gender( registerForm.getGender() );
        registerFormDto.address( registerForm.getAddress() );
        registerFormDto.status( registerForm.getStatus() );
        registerFormDto.createdAt( registerForm.getCreatedAt() );
        registerFormDto.approvedAt( registerForm.getApprovedAt() );
        registerFormDto.approvedBy( registerForm.getApprovedBy() );

        return registerFormDto.build();
    }
}
