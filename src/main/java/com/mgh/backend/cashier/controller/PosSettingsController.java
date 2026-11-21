package com.mgh.backend.cashier.controller;

import com.mgh.backend.cashier.dto.PosSettingsResponseDto;
import com.mgh.backend.cashier.dto.UpdatePosSettingsBatchRequestDto;
import com.mgh.backend.cashier.service.PosSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pos/settings")
@RequiredArgsConstructor
public class PosSettingsController {

    private final PosSettingsService posSettingsService;

    @GetMapping
    public ResponseEntity<PosSettingsResponseDto> getSettings(
            @RequestParam(defaultValue = "GLOBAL") String scopeType,
            @RequestParam(defaultValue = "DEFAULT") String scopeId
    ) {
        return ResponseEntity.ok(posSettingsService.getEffectiveSettings(scopeType, scopeId));
    }

    @PutMapping
    public ResponseEntity<PosSettingsResponseDto> updateSettings(
            @Valid @RequestBody UpdatePosSettingsBatchRequestDto request,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "SYSTEM";
        return ResponseEntity.ok(posSettingsService.updateSettingsBatch(request, username));
    }

    @PostMapping("/reset")
    public ResponseEntity<PosSettingsResponseDto> resetToDefaults(
            @RequestParam(defaultValue = "GLOBAL") String scopeType,
            @RequestParam(defaultValue = "DEFAULT") String scopeId,
            @RequestBody(required = false) List<String> keys,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "SYSTEM";
        return ResponseEntity.ok(posSettingsService.resetKeysToDefaults(keys, scopeType, scopeId, username));
    }
}
