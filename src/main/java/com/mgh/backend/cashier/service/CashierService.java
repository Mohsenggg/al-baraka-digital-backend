package com.mgh.backend.cashier.service;

import com.mgh.backend.cashier.dto.CashierResponseDto;
import com.mgh.backend.cashier.dto.CreateCashierRequest;
import com.mgh.backend.cashier.dto.UpdateCashierRequest;
import com.mgh.backend.cashier.entity.Cashier;
import com.mgh.backend.cashier.repository.CashierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CashierService {

    private final CashierRepository cashierRepository;


    public CashierResponseDto createCashier(CreateCashierRequest request) {
        if (cashierRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (cashierRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Cashier cashier = Cashier.builder()
                .username(request.getUsername())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(request.getPassword())
                .active(true)
                .build();

        return toResponse(cashierRepository.save(cashier));
    }


    @Transactional(readOnly = true)
    public CashierResponseDto getCashier(Long id) {
        Cashier cashier = findCashierById(id);
        return toResponse(cashier);
    }

    @Transactional(readOnly = true)
    public List<CashierResponseDto> getCashiers() {
        return cashierRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }


    public CashierResponseDto updateCashier(Long id, UpdateCashierRequest request) {
        Cashier cashier = findCashierById(id);

        if (StringUtils.hasText(request.getUsername())
                && !request.getUsername().equals(cashier.getUsername())
                && cashierRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (StringUtils.hasText(request.getEmail())
                && !request.getEmail().equals(cashier.getEmail())
                && cashierRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        if (StringUtils.hasText(request.getUsername())) {
            cashier.setUsername(request.getUsername());
        }

        if (StringUtils.hasText(request.getFullName())) {
            cashier.setFullName(request.getFullName());
        }

        if (StringUtils.hasText(request.getEmail())) {
            cashier.setEmail(request.getEmail());
        }

        if (StringUtils.hasText(request.getPassword())) {
            cashier.setPassword(request.getPassword());
        }

        if (request.getActive() != null) {
            cashier.setActive(request.getActive());
        }

        return toResponse(cashierRepository.save(cashier));
    }

    public void deleteCashier(Long id) {
        Cashier cashier = findCashierById(id);
        cashierRepository.delete(cashier);
    }

    private Cashier findCashierById(Long id) {
        return cashierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cashier not found with id: " + id));
    }

    private CashierResponseDto toResponse(Cashier cashier) {
        return CashierResponseDto.builder()
                .id(cashier.getId())
                .username(cashier.getUsername())
                .fullName(cashier.getFullName())
                .email(cashier.getEmail())
                .active(cashier.isActive())
                .createdAt(cashier.getCreatedAt())
                .updatedAt(cashier.getUpdatedAt())
                .build();
    }
}