//package com.mgh.backend.auth.service;
//
//import com.mgh.backend.auth.domain.dto.register.*;
//import com.mgh.backend.auth.domain.entity.RegisterForm;
//import com.mgh.backend.auth.domain.entity.UserAuth;
//import com.mgh.backend.auth.domain.entity.UserProfile;
//import com.mgh.backend.auth.domain.enums.RegisterStatus;
//import com.mgh.backend.auth.domain.enums.Role;
//import com.mgh.backend.auth.repository.RegisterFormRepository;
//import com.mgh.backend.auth.repository.UserAuthRepo;
//import com.mgh.backend.auth.repository.UserProfileRepository;
//import com.mgh.backend.invitation.service.InvitationQuizService;
//import com.mgh.backend.tree.domain.entity.Node;
//import com.mgh.backend.tree.domain.enums.TreeNodeStatus;
//import com.mgh.backend.tree.repository.NodeRepo;
//import jakarta.persistence.EntityNotFoundException;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.nio.charset.StandardCharsets;
//import java.time.LocalDateTime;
//import java.util.Base64;
//
//@Service
//public class RegistrationService {
//
//    private final NodeRepo nodeRepo;
//    private final RegisterFormRepository registerFormRepository;
//    private final UserAuthRepo userAuthRepo;
//    private final UserProfileRepository userProfileRepository;
//    private final PasswordEncoder passwordEncoder;
//    private final InvitationQuizService invitationQuizService;
//
//
//    public RegistrationService(
//                               NodeRepo nodeRepo,
//                               RegisterFormRepository registerFormRepository,
//                               UserAuthRepo userAuthRepo,
//                               UserProfileRepository userProfileRepository,
//                               PasswordEncoder passwordEncoder,
//                               InvitationQuizService invitationQuizService) {
//        this.nodeRepo = nodeRepo;
//        this.registerFormRepository = registerFormRepository;
//        this.userAuthRepo = userAuthRepo;
//        this.userProfileRepository = userProfileRepository;
//        this.passwordEncoder = passwordEncoder;
//        this.invitationQuizService = invitationQuizService;
//    }
//
//    @Transactional(readOnly = true)
//    public RegistrationInitiateResponseDto initiateRegistration(RegistrationInitiateRequestDto request) {
//        Long nodeId = validateAndExtractNodeId(request.getInvitationCode());
//
//        Node node = nodeRepo.findById(nodeId)
//                .orElseThrow(() -> new EntityNotFoundException("Node not found"));
//
//        if (!request.getInvitationCode().equals(node.getInvitationCode())) {
//            throw new IllegalArgumentException("Invitation code does not match this node");
//        }
//
//        TreeNodeStatus status = node.getStatus();
//        if (status == TreeNodeStatus.INACTIVE) {
//            return RegistrationInitiateResponseDto.builder()
//                    .firstName(node.getNodeName())
//                    .parentName(node.getNodeParentName())
//                    .status(status)
//                    .message("Register Now")
//                    .build();
//        } else if (status == TreeNodeStatus.PENDING) {
//            return RegistrationInitiateResponseDto.builder()
//                    .status(status)
//                    .message("Your registration is waiting for approval.")
//                    .build();
//        } else {
//            return RegistrationInitiateResponseDto.builder()
//                    .status(status)
//                    .message("Your profile is already activated. Please login.")
//                    .build();
//        }
//    }
//
//
//
//    @Transactional
//    public String directApprove(RegistrationSubmitRequestDto request) {
//        Long registerFormId = submitRegistration(request);
//        String fullname = approveRegistration(registerFormId, "Self Approved");
//        return fullname;
//    }
//
//    @Transactional
//    public Long submitRegistration(RegistrationSubmitRequestDto request) {
//        Long nodeId = validateAndExtractNodeId(request.getInvitationCode());
//
//        Node node = nodeRepo.findById(nodeId)
//                .orElseThrow(() -> new EntityNotFoundException("Node not found"));
//
//        if (!request.getInvitationCode().equals(node.getInvitationCode())) {
//            throw new IllegalArgumentException("Invitation code does not match this node");
//        }
//
//        if (node.getStatus() == TreeNodeStatus.ACTIVATED) {
//            throw new IllegalStateException("Profile already activated");
//        }
//
//        String username = request.getUsername() != null && !request.getUsername().isBlank()
//                ? request.getUsername()
//                : request.getPhoneNumber();
//
//        String encodedPassword = passwordEncoder.encode(request.getPassword()); // phone used as initial password placeholder
//
//
//        RegisterForm registerForm = RegisterForm.builder()
//                .nodeId(node.getId())
//                .username(username)
//                .email(request.getEmail())
//                .phone(request.getPhoneNumber())
//                .birthDate(request.getBirthDate())
//                .gender(request.getGender())
//                .address(request.getAddress())
//                .password(encodedPassword)
//                .status(RegisterStatus.SUBMITTED)
//                .build();
//
//        RegisterForm savedForm = registerFormRepository.save(registerForm);
//
//        node.setStatus(TreeNodeStatus.PENDING);
//        nodeRepo.save(node);
//
//        return savedForm.getId();
//    }
//
//    @Transactional
//    public String approveRegistration(Long registerFormId, String approvedBy) {
//        RegisterForm registerForm = registerFormRepository.findById(registerFormId)
//                .orElseThrow(() -> new EntityNotFoundException("Register form not found"));
//
//        if (registerForm.getStatus() == RegisterStatus.APPROVED) {
//            return approvedBy;
//        }
//
//        Node node = nodeRepo.findById(registerForm.getNodeId())
//                .orElseThrow(() -> new EntityNotFoundException("Node not found"));
//
//
//        UserAuth userAuth = UserAuth.builder()
//                .username(registerForm.getUsername())
//                .email(registerForm.getEmail())
//                .password(registerForm.getPassword())
//                .phone(registerForm.getPhone())
//                .fullName(node.getNodeName() + " " + node.getNodeParentName())
//                .role(Role.USER)
//                .enabled(true)
//                .locked(false)
//                .build();
//
//        UserAuth savedUser = userAuthRepo.save(userAuth);
//
//        UserProfile userProfile = UserProfile.builder()
//                .userAuth(userAuth)
//                .birthDate(registerForm.getBirthDate())
//                .gender(registerForm.getGender())
//                .address(registerForm.getAddress())
//                .build();
//
//        UserProfile savedProfile = userProfileRepository.save(userProfile);
//
//        node.setUserId(userAuth.getId());
//        node.setStatus(TreeNodeStatus.ACTIVATED);
//        nodeRepo.save(node);
//
//        registerForm.setStatus(RegisterStatus.APPROVED);
//        registerForm.setApprovedAt(LocalDateTime.now());
//        registerForm.setApprovedBy(approvedBy);
//        registerFormRepository.save(registerForm);
//
//        return savedUser.getFullName();
//    }
//
//
//    @Transactional
//    public InvitationCodeResponseDto generateInvitationCode(InvitationCodeGenerateRequestDto request, Long userId) {
//        invitationQuizService.assertEligibleForInvitationGeneration(userId);
//
//        Long nodeId = request.getNodeId();
//        Node node = nodeRepo.findById(nodeId)
//                .orElseThrow(() -> new EntityNotFoundException("Node not found"));
//
//        String payload = "nodeId:" + node.getId();
//        String encrypted = Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
//
//        node.setInvitationCode(encrypted);
//        node.setStatus(TreeNodeStatus.INACTIVE);
//        nodeRepo.save(node);
//
//        invitationQuizService.consumePassedSessionAfterSuccessfulInvite(userId);
//
//        return new InvitationCodeResponseDto(encrypted);
//    }
//
//    public Long validateAndExtractNodeId(String invitationCode) {
//        String decoded;
//        try {
//            decoded = new String(Base64.getUrlDecoder().decode(invitationCode), StandardCharsets.UTF_8);
//        } catch (IllegalArgumentException ex) {
//            throw new IllegalArgumentException("Invalid invitation code");
//        }
//
//        if (!decoded.startsWith("nodeId:")) {
//            throw new IllegalArgumentException("Invalid invitation code");
//        }
//
//        String idPart = decoded.substring("nodeId:".length());
//        try {
//            return Long.parseLong(idPart);
//        } catch (NumberFormatException ex) {
//            throw new IllegalArgumentException("Invalid invitation code");
//        }
//    }
//}
//
