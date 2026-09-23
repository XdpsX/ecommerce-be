package com.xdpsx.ecommerce.constants;

public class SecurityConstants {
    public static final String[] PUBLIC_ENDPOINTS = {"/auth/**", "/oauth2/**", "/swagger-ui/**", "/v3/api-docs/**"};
    public static final String[] PUBLIC_GET_ENDPOINTS = {
        "/categories/get-all",
        "/categories/*/brands",
        "/categories/*/products",
        "/categories/*",
        "/products/*",
        "/products",
        "/products/slug/*"
    };

    public static final String ROLE_PREFIX = "ROLE_";
}
