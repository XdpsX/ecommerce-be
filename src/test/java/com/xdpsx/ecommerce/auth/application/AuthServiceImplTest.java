package com.xdpsx.ecommerce.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.xdpsx.ecommerce.auth.api.dto.LoginRequest;
import com.xdpsx.ecommerce.auth.api.dto.RegisterRequest;
import com.xdpsx.ecommerce.auth.infrastructure.security.CustomUserDetails;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshSessionManager refreshSessionManager;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void register_ShouldPersistCanonicalLocalUserAndGenerateToken() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Customer")
                .email("  Alice@Example.COM ")
                .password("password123")
                .build();
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshSessionManager.issue(any(User.class))).thenReturn(session("token"));

        AuthenticatedSession response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("token");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        verify(refreshSessionManager).issue(saved);
    }

    @Test
    void login_ShouldAuthenticateWithCanonicalEmailAndOriginalPassword() {
        LoginRequest request = LoginRequest.builder()
                .email("  Alice@Example.COM ")
                .password("password123")
                .build();
        CustomUserDetails principal = CustomUserDetails.builder()
                .id(1L)
                .username("alice@example.com")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(refreshSessionManager.issue(any(User.class))).thenReturn(session("token"));

        AuthenticatedSession response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("token");
        ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getName()).isEqualTo("alice@example.com");
        assertThat(tokenCaptor.getValue().getCredentials()).isEqualTo("password123");
        verify(refreshSessionManager).issue(any(User.class));
    }

    @Test
    void register_ShouldRejectOrdinaryDuplicateBeforePersistence() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Customer")
                .email(" Alice@Example.COM ")
                .password("password123")
                .build();
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void register_ShouldTranslateNamedEmailUniquenessRace() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Customer")
                .email("alice@example.com")
                .password("password123")
                .build();
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key", new SQLException("Duplicate entry for key uk_users_email")));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS));
    }

    @Test
    void register_ShouldRethrowUnrelatedIntegrityFailure() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Customer")
                .email("alice@example.com")
                .password("password123")
                .build();
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("invalid role", new SQLException("ck_users_role"));
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> authService.register(request)).isSameAs(failure);
    }

    @Test
    void refresh_ShouldTranslateAnyRotationFailureToOneStableError() {
        when(refreshSessionManager.rotate("invalid")).thenReturn(RefreshRotationOutcome.invalid());

        assertThatThrownBy(() -> authService.refresh("invalid"))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_CREDENTIAL));
    }

    @Test
    void logout_ShouldDelegateAndRemainIdempotentAtApplicationBoundary() {
        authService.logout("credential");

        verify(refreshSessionManager).revoke("credential");
    }

    private static AuthenticatedSession session(String token) {
        return new AuthenticatedSession(token, "session.secret", java.time.Instant.parse("2026-11-01T00:00:00Z"));
    }
}
