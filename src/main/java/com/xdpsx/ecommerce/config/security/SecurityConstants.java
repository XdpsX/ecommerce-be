package com.xdpsx.ecommerce.config.security;

public class SecurityConstants {
    public static final String[] PUBLIC_ENDPOINTS = {
        "/auth/**", "/oauth2/**", "/login/oauth2/**", "/swagger-ui/**", "/v3/api-docs/**", "/error"
    };
    public static final String[] PUBLIC_GET_ENDPOINTS = {
        "/categories/**", "/brands/**", "/products/**", "/payments/vnpay_ipn"
    };

    public static final String ROLE_PREFIX = "ROLE_";
}
