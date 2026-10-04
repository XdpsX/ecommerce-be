package com.xdpsx.ecommerce.cart.api;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.config.CartProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GuestCartRequestGuard {
    private final CartProperties properties;

    public boolean isAllowed(HttpServletRequest request) {
        return properties.getGuardHeaderValue().equals(request.getHeader(properties.getGuardHeaderName()));
    }
}
