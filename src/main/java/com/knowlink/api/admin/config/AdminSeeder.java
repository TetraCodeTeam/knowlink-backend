package com.knowlink.api.admin.config;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates the configured administrator accounts on startup. Idempotent: existing
 * emails are never modified or duplicated. Credentials are never logged.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final AdminSeedProperties properties;
    private final IUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        List<AdminSeedProperties.AdminAccount> accounts = properties.getAccounts();
        if (accounts == null || accounts.isEmpty()) {
            log.warn("No admin accounts configured; skipping admin seeding");
            return;
        }

        for (AdminSeedProperties.AdminAccount account : accounts) {
            seed(account);
        }
    }

    private void seed(AdminSeedProperties.AdminAccount account) {
        if (isBlank(account.getEmail()) || isBlank(account.getPassword()) || isBlank(account.getFullName())) {
            log.warn("Skipping incomplete admin account configuration (email, password and full name are required)");
            return;
        }

        String email = account.getEmail().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            log.info("Admin account already exists, skipping seeding for {}", email);
            return;
        }

        User admin = User.builder()
                .email(email)
                .password(passwordEncoder.encode(account.getPassword()))
                .fullName(account.getFullName().trim())
                .role(Role.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        userRepository.save(admin);
        log.info("Admin seeded for {}", email);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}