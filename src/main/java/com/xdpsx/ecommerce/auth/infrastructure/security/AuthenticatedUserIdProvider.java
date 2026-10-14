package com.xdpsx.ecommerce.auth.infrastructure.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;

@Component
public class AuthenticatedUserIdProvider implements CurrentUserIdProvider {
    @Override
    public Long getRequiredUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated user ID is required");
        }

        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails && userDetails.getId() != null) {
            return userDetails.getId();
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Object userIdClaim = jwtAuthentication.getToken().getClaim("uid");
            if (userIdClaim instanceof Number userId && userId.longValue() > 0) {
                return userId.longValue();
            }
        }

        throw new AuthenticationCredentialsNotFoundException("Authenticated user ID is required");
    }
}
