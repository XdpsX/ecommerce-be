package com.xdpsx.ecommerce.auth.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.auth.domain.RefreshSession;
import com.xdpsx.ecommerce.auth.infrastructure.security.TokenProvider;
import com.xdpsx.ecommerce.auth.persistence.RefreshSessionRepository;
import com.xdpsx.ecommerce.config.AuthSessionProperties;
import com.xdpsx.ecommerce.user.domain.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshSessionManager {
    private final RefreshSessionRepository refreshSessionRepository;
    private final RefreshCredentialService credentialService;
    private final TokenProvider tokenProvider;
    private final AuthSessionProperties properties;
    private final Clock clock;

    @Transactional
    public AuthenticatedSession issue(User user) {
        Instant now = clock.instant();
        String sessionId = credentialService.newSessionId();
        RefreshCredentialService.IssuedCredential credential = credentialService.issue(sessionId);
        RefreshSession session = RefreshSession.builder()
                .id(sessionId)
                .user(user)
                .secretHash(credential.secretHash())
                .createdAt(now)
                .expiresAt(now.plus(properties.getRefreshSessionLifetime()))
                .build();
        refreshSessionRepository.save(session);
        return new AuthenticatedSession(
                tokenProvider.generateLocalToken(user), credential.rawCredential(), session.getExpiresAt());
    }

    @Transactional
    public RefreshRotationOutcome rotate(String rawCredential) {
        Instant now = clock.instant();
        var parsed = credentialService.parse(rawCredential);
        if (parsed.isEmpty()) {
            return RefreshRotationOutcome.invalid();
        }
        RefreshCredentialService.ParsedCredential credential = parsed.get();
        var sessionOptional = refreshSessionRepository.findByIdForUpdate(credential.sessionId());
        if (sessionOptional.isEmpty()) {
            return RefreshRotationOutcome.invalid();
        }
        RefreshSession session = sessionOptional.get();
        if (session.isRevoked() || session.isExpired(now)) {
            return RefreshRotationOutcome.invalid();
        }
        if (!credentialService.matches(session.getSecretHash(), credential.secret())) {
            session.revoke(now);
            return RefreshRotationOutcome.invalid();
        }

        RefreshCredentialService.IssuedCredential replacement = credentialService.issue(session.getId());
        session.replaceSecretHash(replacement.secretHash());
        return new RefreshRotationOutcome(
                true,
                tokenProvider.generateLocalToken(session.getUser()),
                replacement.rawCredential(),
                session.getExpiresAt());
    }

    @Transactional
    public void revoke(String rawCredential) {
        var parsed = credentialService.parse(rawCredential);
        if (parsed.isEmpty()) {
            return;
        }
        RefreshCredentialService.ParsedCredential credential = parsed.get();
        var sessionOptional = refreshSessionRepository.findByIdForUpdate(credential.sessionId());
        if (sessionOptional.isEmpty()) {
            return;
        }
        RefreshSession session = sessionOptional.get();
        if (!session.isRevoked() && credentialService.matches(session.getSecretHash(), credential.secret())) {
            session.revoke(clock.instant());
        }
    }
}
