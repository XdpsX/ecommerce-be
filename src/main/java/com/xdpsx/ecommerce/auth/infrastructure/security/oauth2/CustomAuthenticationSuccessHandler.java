package com.xdpsx.ecommerce.auth.infrastructure.security.oauth2;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.xdpsx.ecommerce.auth.infrastructure.security.CustomUserDetails;
import com.xdpsx.ecommerce.auth.infrastructure.security.TokenProvider;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final TokenProvider tokenProvider;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        handle(request, response, authentication);
        super.clearAuthenticationAttributes(request);
    }

    @Override
    protected void handle(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        String targetUrl = redirectUri.isEmpty() ? determineTargetUrl(request, response, authentication) : redirectUri;

        String token = tokenProvider.generateToken((CustomUserDetails) authentication.getPrincipal());
        targetUrl = UriComponentsBuilder.fromUriString(targetUrl)
                .queryParam("token", token)
                .build()
                .toUriString();
        log.info("Login successfully. Redirecting to {}", targetUrl);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
