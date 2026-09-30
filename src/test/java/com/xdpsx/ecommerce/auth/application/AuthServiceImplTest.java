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
import com.xdpsx.ecommerce.auth.api.dto.TokenResponse;
import com.xdpsx.ecommerce.auth.infrastructure.security.CustomUserDetails;
import com.xdpsx.ecommerce.auth.infrastructure.security.TokenProvider;
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
    private TokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

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
        when(tokenProvider.generateToken(any(User.class))).thenReturn("token");

        TokenResponse response = authService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("token");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        verify(tokenProvider).generateToken(saved);
    }

    @Test
    void login_ShouldAuthenticateWithCanonicalEmailAndOriginalPassword() {
        LoginRequest request = LoginRequest.builder()
                .email("  Alice@Example.COM ")
                .password("password123")
                .build();
        CustomUserDetails principal = CustomUserDetails.builder()
                .username("alice@example.com")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenProvider.generateToken(principal)).thenReturn("token");

        TokenResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("token");
        ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getName()).isEqualTo("alice@example.com");
        assertThat(tokenCaptor.getValue().getCredentials()).isEqualTo("password123");
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
}
