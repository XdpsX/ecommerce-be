package com.xdpsx.ecommerce.user.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DevAdminBootstrapRunner implements ApplicationRunner {
    private final UserRepository userRepository;

    @Value("${app.bootstrap-admin.email:}")
    private String email;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String canonicalEmail = EmailIdentity.canonicalize(email);
        if (canonicalEmail == null || canonicalEmail.isBlank()) {
            throw new IllegalStateException("app.bootstrap-admin.email is required when admin bootstrap is enabled");
        }

        userRepository
                .findByEmailForUpdate(canonicalEmail)
                .ifPresentOrElse(
                        user -> {
                            if (user.getAuthProvider() != AuthProvider.LOCAL) {
                                log.warn("Dev admin bootstrap skipped: the configured account is not a LOCAL user");
                                return;
                            }
                            if (user.getRole() == Role.USER) {
                                user.setRole(Role.ADMIN);
                                log.info("Promoted the configured LOCAL user to ADMIN for dev");
                            } else {
                                log.info(
                                        "Dev admin bootstrap made no change because the configured LOCAL user is already ADMIN");
                            }
                        },
                        () -> log.info(
                                "Dev admin bootstrap found no account; register the configured LOCAL user and restart"));
    }
}
