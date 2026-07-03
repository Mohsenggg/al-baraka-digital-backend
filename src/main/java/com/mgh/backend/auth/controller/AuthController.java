package com.mgh.backend.auth.controller;


import com.mgh.backend.auth.domain.dto.AuthRequestDto;
import com.mgh.backend.auth.domain.dto.AuthResponseDto;
import com.mgh.backend.auth.domain.dto.RegisterRequestDto;
import com.mgh.backend.auth.security.SecurityUtils;
import com.mgh.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:4200", "https://rahem-social.web.app"}) // should be removed and configured with the filter chain

public class AuthController {

    private final AuthService authService;

    public AuthController( AuthService authService) {
        this.authService = authService;
    }


    @PostMapping("/register")
    public ResponseEntity register(@RequestBody RegisterRequestDto request){
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody AuthRequestDto request){
        AuthResponseDto authenticationResponse = authService.login(request);
        return ResponseEntity.ok(authenticationResponse);
    }




}
