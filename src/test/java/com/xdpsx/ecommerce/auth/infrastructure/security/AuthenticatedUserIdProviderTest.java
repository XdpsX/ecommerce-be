package com.xdpsx.ecommerce.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.xdpsx.ecommerce.user.domain.Role;

class AuthenticatedUserIdProviderTest {
    private final AuthenticatedUserIdProvider provider = new AuthenticatedUserIdProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getRequiredUserId_ShouldReadIdFromAuthenticatedPrincipal() {
        CustomUserDetails principal =
                CustomUserDetails.builder().id(42L).role(Role.ADMIN).build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        assertThat(provider.getRequiredUserId()).isEqualTo(42L);
    }

    @Test
    void getRequiredUserId_ShouldReadIdFromBearerJwtUidClaim() {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject("admin@example.com")
                .claim("uid", 84L)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

        assertThat(provider.getRequiredUserId()).isEqualTo(84L);
    }

    @Test
    void getRequiredUserId_ShouldRejectBearerJwtWithoutUidClaim() {
        Jwt jwt = Jwt.withTokenValue("legacy-access-token")
                .header("alg", "HS256")
                .subject("admin@example.com")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

        assertThatThrownBy(provider::getRequiredUserId).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void getRequiredUserId_ShouldRejectMissingOrUnrecognizedPrincipal() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated("admin", null, java.util.List.of()));

        assertThatThrownBy(provider::getRequiredUserId).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
