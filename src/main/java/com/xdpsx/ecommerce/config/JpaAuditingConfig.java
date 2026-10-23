package com.xdpsx.ecommerce.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", modifyOnCreate = true)
public class JpaAuditingConfig {
    @Bean
    AuditorAware<Long> auditorAware(CurrentUserIdProvider currentUserIdProvider) {
        return () -> {
            try {
                return Optional.of(currentUserIdProvider.getRequiredUserId());
            } catch (AuthenticationCredentialsNotFoundException exception) {
                // Timestamp auditing also runs for background work and unauthenticated
                // persistence tests; those writes have no user actor to record.
                return Optional.empty();
            }
        };
    }
}
