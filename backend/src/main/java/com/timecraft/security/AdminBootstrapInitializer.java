package com.timecraft.security;

import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.entity.AccountStatus;
import com.timecraft.authentication.entity.Role;
import com.timecraft.authentication.entity.RoleName;
import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.RoleRepository;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Controlled bootstrap of the initial ADMIN account from ADMIN_EMAIL and ADMIN_PASSWORD
 * environment variables. There is no public admin registration endpoint anywhere.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.admin().email();
        String password = properties.admin().password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            long admins = userRepository.countByRoleName(RoleName.ADMIN);
            if (admins == 0) {
                log.info("No ADMIN account exists yet. Set ADMIN_EMAIL and ADMIN_PASSWORD to bootstrap the initial administrator.");
            }
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing from roles table"));

        String normalizedEmail = email.trim().toLowerCase();
        User admin = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (admin == null) {
            admin = User.builder()
                    .email(normalizedEmail)
                    .passwordHash(passwordEncoder.encode(password))
                    .firstName(properties.admin().firstName() != null ? properties.admin().firstName() : "Store")
                    .lastName(properties.admin().lastName() != null ? properties.admin().lastName() : "Administrator")
                    .emailVerified(true)
                    .accountStatus(AccountStatus.ACTIVE)
                    .roles(Set.of(adminRole))
                    .build();
            admin = userRepository.save(admin);
            auditLogService.log(admin.getId(), admin.getEmail(), "ADMIN_BOOTSTRAPPED",
                    "USER", String.valueOf(admin.getId()), "Initial admin account created from environment configuration", "localhost");
            log.info("Bootstrapped initial ADMIN account for {}", normalizedEmail);
        } else if (!admin.hasRole(RoleName.ADMIN)) {
            admin.getRoles().add(adminRole);
            userRepository.save(admin);
            auditLogService.log(admin.getId(), admin.getEmail(), "ADMIN_ROLE_GRANTED",
                    "USER", String.valueOf(admin.getId()), "Granted ADMIN role via bootstrap configuration", "localhost");
        }
    }
}
