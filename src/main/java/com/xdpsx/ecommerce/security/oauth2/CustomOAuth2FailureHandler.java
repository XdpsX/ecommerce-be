package com.xdpsx.ecommerce.security.oauth2;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CustomOAuth2FailureHandler implements AuthenticationFailureHandler {
    @Value("${app.oauth2.error-uri}")
    private String ERROR_URL;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.error(exception.getMessage(), exception);
        response.sendRedirect(ERROR_URL);
    }
}
