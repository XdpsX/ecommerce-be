package com.xdpsx.ecommerce.auth.api;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.config.AuthSessionProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefreshRequestGuard {
    private final AuthSessionProperties properties;

    public boolean isAllowed(HttpServletRequest request) {
        return properties.getGuardHeaderValue().equals(request.getHeader(properties.getGuardHeaderName()));
    }
}
