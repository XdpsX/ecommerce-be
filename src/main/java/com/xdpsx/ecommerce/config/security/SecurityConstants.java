package com.xdpsx.ecommerce.config.security;

public class SecurityConstants {
    public static final String[] PUBLIC_ENDPOINTS = {"/auth/**", "/oauth2/**", "/swagger-ui/**", "/v3/api-docs/**"};
    public static final String[] PUBLIC_GET_ENDPOINTS = {
        "/categories",
        "/categories/*",
        "/categories/*/brands",
        "/categories/*/products",
        "/products/*",
        "/products",
        "/products/slug/*"
    };

    public static final String ROLE_PREFIX = "ROLE_";
}
