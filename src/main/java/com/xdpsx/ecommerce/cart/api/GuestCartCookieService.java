package com.xdpsx.ecommerce.cart.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.config.CartProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GuestCartCookieService {
    private final CartProperties properties;
    private final Clock clock;

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (properties.getGuestCookieName().equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    public ResponseCookie create(String credential, Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(clock.instant(), expiresAt).getSeconds());
        return base(credential).maxAge(maxAge).build();
    }

    public ResponseCookie expire() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.getGuestCookieName(), value)
                .httpOnly(true)
                .secure(properties.isGuestCookieSecure())
                .path(properties.getGuestCookiePath())
                .sameSite(properties.getGuestCookieSameSite());
    }
}
