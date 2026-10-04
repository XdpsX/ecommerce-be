package com.xdpsx.ecommerce.cart.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import com.xdpsx.ecommerce.config.CartProperties;

class GuestCartCookieServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private CartProperties properties;
    private GuestCartCookieService service;

    @BeforeEach
    void setUp() {
        properties = new CartProperties();
        properties.setGuestCookieSecure(true);
        properties.setGuestCookieSameSite("Strict");
        properties.setGuestCookiePath("/cart");
        service = new GuestCartCookieService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void create_ShouldApplyConfiguredSecurityAttributesAndLifetime() {
        ResponseCookie cookie = service.create("42.secret", NOW.plus(Duration.ofHours(2)));

        assertThat(cookie.getValue()).isEqualTo("42.secret");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/cart");
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void readAndExpire_ShouldUseConfiguredNameAndZeroLifetime() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("guest_cart", "42.secret"));

        assertThat(service.read(request)).isEqualTo("42.secret");
        assertThat(service.expire().getMaxAge()).isEqualTo(Duration.ZERO);
    }
}
