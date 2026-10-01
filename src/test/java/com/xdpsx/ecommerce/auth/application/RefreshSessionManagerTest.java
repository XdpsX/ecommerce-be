package com.xdpsx.ecommerce.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.auth.domain.RefreshSession;
import com.xdpsx.ecommerce.auth.infrastructure.security.TokenProvider;
import com.xdpsx.ecommerce.auth.persistence.RefreshSessionRepository;
import com.xdpsx.ecommerce.config.AuthSessionProperties;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;

@ExtendWith(MockitoExtension.class)
class RefreshSessionManagerTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Mock
    private RefreshSessionRepository repository;

    @Mock
    private TokenProvider tokenProvider;

    private RefreshCredentialService credentialService;
    private AuthSessionProperties properties;
    private RefreshSessionManager manager;
    private User user;

    @BeforeEach
    void setUp() {
        credentialService = new RefreshCredentialService();
        properties = new AuthSessionProperties();
        properties.setRefreshSessionLifetime(Duration.ofDays(30));
        manager = new RefreshSessionManager(
                repository, credentialService, tokenProvider, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.builder()
                .id(7L)
                .email("user@example.com")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();
    }

    @Test
    void issue_ShouldPersistHashOnlyAndSetAbsoluteExpiry() {
        when(tokenProvider.generateLocalToken(user)).thenReturn("access");

        AuthenticatedSession issued = manager.issue(user);

        assertThat(issued.accessToken()).isEqualTo("access");
        assertThat(issued.refreshCredential()).contains(".");
        assertThat(issued.refreshExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        org.mockito.ArgumentCaptor<RefreshSession> captor = org.mockito.ArgumentCaptor.forClass(RefreshSession.class);
        verify(repository).save(captor.capture());
        RefreshSession persisted = captor.getValue();
        RefreshCredentialService.ParsedCredential parsed =
                credentialService.parse(issued.refreshCredential()).orElseThrow();
        assertThat(persisted.getSecretHash()).hasSize(32);
        assertThat(persisted.getSecretHash()).isNotEqualTo(parsed.secret());
        assertThat(credentialService.matches(persisted.getSecretHash(), parsed.secret()))
                .isTrue();
        assertThat(persisted.getCreatedAt()).isEqualTo(NOW);
        assertThat(persisted.getExpiresAt()).isEqualTo(issued.refreshExpiresAt());
    }

    @Test
    void rotate_ShouldReplaceHashAndRetainOriginalExpiry() {
        String sessionId = credentialService.newSessionId();
        RefreshCredentialService.IssuedCredential original = credentialService.issue(sessionId);
        Instant expiry = NOW.plus(Duration.ofDays(30));
        RefreshSession session = RefreshSession.builder()
                .id(sessionId)
                .user(user)
                .secretHash(original.secretHash())
                .createdAt(NOW)
                .expiresAt(expiry)
                .build();
        when(repository.findByIdForUpdate(sessionId)).thenReturn(java.util.Optional.of(session));
        when(tokenProvider.generateLocalToken(user)).thenReturn("rotated-access");

        RefreshRotationOutcome rotated = manager.rotate(original.rawCredential());

        assertThat(rotated.successful()).isTrue();
        assertThat(rotated.accessToken()).isEqualTo("rotated-access");
        assertThat(rotated.refreshCredential()).isNotEqualTo(original.rawCredential());
        assertThat(rotated.refreshExpiresAt()).isEqualTo(expiry);
        assertThat(session.getSecretHash()).isNotEqualTo(original.secretHash());
    }

    @Test
    void rotate_ShouldRevokeOnSecretReuseAndReturnGenericFailure() {
        String sessionId = credentialService.newSessionId();
        RefreshCredentialService.IssuedCredential current = credentialService.issue(sessionId);
        RefreshSession session = RefreshSession.builder()
                .id(sessionId)
                .user(user)
                .secretHash(current.secretHash())
                .createdAt(NOW)
                .expiresAt(NOW.plus(Duration.ofDays(30)))
                .build();
        when(repository.findByIdForUpdate(sessionId)).thenReturn(java.util.Optional.of(session));

        RefreshRotationOutcome first = manager.rotate(current.rawCredential());
        RefreshRotationOutcome reused = manager.rotate(current.rawCredential());

        assertThat(first.successful()).isTrue();
        assertThat(reused.successful()).isFalse();
        assertThat(session.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void revoke_ShouldOnlyRevokeMatchingCurrentCredential() {
        String sessionId = credentialService.newSessionId();
        RefreshCredentialService.IssuedCredential current = credentialService.issue(sessionId);
        RefreshSession session = RefreshSession.builder()
                .id(sessionId)
                .user(user)
                .secretHash(current.secretHash())
                .createdAt(NOW)
                .expiresAt(NOW.plus(Duration.ofDays(30)))
                .build();
        when(repository.findByIdForUpdate(sessionId)).thenReturn(java.util.Optional.of(session));

        manager.revoke(sessionId + ".invalid");
        assertThat(session.getRevokedAt()).isNull();

        manager.revoke(current.rawCredential());
        assertThat(session.getRevokedAt()).isEqualTo(NOW);
    }
}
