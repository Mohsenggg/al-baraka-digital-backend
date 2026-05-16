//package com.mgh.backend.auth.controller;
//
//
//import com.mgh.backend.auth.domain.dto.AuthRequestDto;
//import com.mgh.backend.auth.domain.dto.AuthResponseDto;
//import com.mgh.backend.auth.domain.dto.RegisterRequestDto;
//import com.mgh.backend.auth.domain.dto.register.*;
//import com.mgh.backend.auth.security.SecurityUtils;
//import com.mgh.backend.auth.service.AuthService;
//import com.mgh.backend.auth.service.RegistrationService;
//import jakarta.validation.Valid;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.security.core.Authentication;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/auth")
//@CrossOrigin(origins = "http://localhost:4200") // should be removed and configured with the filter chain
//
//public class AuthController {
//
//    private final RegistrationService registrationService;
//    private final AuthService authService;
//
//    public AuthController(RegistrationService registrationService, AuthService authService) {
//        this.registrationService = registrationService;
//        this.authService = authService;
//    }
//
//
//    @PostMapping("/register")
//    public ResponseEntity register(@RequestBody RegisterRequestDto request){
//        return authService.register(request);
//    }
//
//    @PostMapping("/login")
//    public ResponseEntity login(@RequestBody AuthRequestDto request){
//        AuthResponseDto authenticationResponse = authService.login(request);
//        return ResponseEntity.ok(authenticationResponse);
//    }
//
//    @PostMapping("/invitation/generate")
//    public ResponseEntity<InvitationCodeResponseDto> generateInvitation(
//            @RequestBody @Valid InvitationCodeGenerateRequestDto request,
//            Authentication authentication) {
//        long userId = SecurityUtils.requireUserId(authentication);
//        InvitationCodeResponseDto response = registrationService.generateInvitationCode(request, userId);
//        return ResponseEntity.ok(response);
//    }
//
//    @PostMapping("/invitation/check")
//    public ResponseEntity<RegistrationInitiateResponseDto> initiate(@RequestBody @Valid RegistrationInitiateRequestDto request) {
//        RegistrationInitiateResponseDto response = registrationService.initiateRegistration(request);
//        return ResponseEntity.ok(response);
//    }
//
//    @PostMapping("/register/submit")
//    public ResponseEntity<Void> submit(@RequestBody @Valid RegistrationSubmitRequestDto request) {
//        registrationService.submitRegistration(request);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/register/submit/approve")
//    public ResponseEntity<String> directApprove(@RequestBody @Valid RegistrationSubmitRequestDto request) {
//        String fullName = registrationService.directApprove(request);
//        return ResponseEntity.ok("Welcome "+fullName +" Your Account Created Successfully.");
//    }
//
//    @PostMapping("/{id}/approve")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<String> approve(@PathVariable("id") Long id, Authentication authentication) {
//        String approvedBy = authentication != null ? authentication.getName() : "system";
//        String fullName = registrationService.approveRegistration(id, approvedBy);
//        return ResponseEntity.ok("Welcome "+fullName +" Your Account Created Successfully.");
//    }
//}
