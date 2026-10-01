package com.xdpsx.ecommerce.auth.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.config.AuthSessionProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefreshCookieService {
    private final AuthSessionProperties properties;
    private final Clock clock;

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (properties.getRefreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public ResponseCookie create(String value, Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(clock.instant(), expiresAt).getSeconds());
        return base(value).maxAge(maxAge).build();
    }

    public ResponseCookie expire() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.isRefreshCookieSecure())
                .path(properties.getRefreshCookiePath())
                .sameSite(properties.getRefreshCookieSameSite());
    }
}
