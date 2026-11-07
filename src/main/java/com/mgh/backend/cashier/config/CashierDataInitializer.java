package com.mgh.backend.cashier.config;

import com.mgh.backend.cashier.entity.Cashier;
import com.mgh.backend.cashier.repository.CashierRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CashierDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CashierDataInitializer.class);
    private final CashierRepository cashierRepository;

    @Override
    public void run(String... args) {
        if (cashierRepository.count() == 0) {
            log.info("No cashiers found in database. Seeding default cashier (ID: 1)...");
            LocalDateTime now = LocalDateTime.now();
            Cashier defaultCashier = Cashier.builder()
                    .username("default_cashier")
                    .fullName("الكاشير الافتراضي")
                    .email("cashier@baraka.com")
                    .password("123456")
                    .active(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            cashierRepository.save(defaultCashier);
            log.info("Default cashier seeded successfully with id: {}", defaultCashier.getId());
        }
    }
}
