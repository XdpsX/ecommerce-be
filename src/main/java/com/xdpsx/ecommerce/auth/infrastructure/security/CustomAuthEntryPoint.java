package com.xdpsx.ecommerce.auth.infrastructure.security;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.common.error.ApiProblemFactory;
import com.xdpsx.ecommerce.common.error.ErrorCode;

import tools.jackson.databind.ObjectMapper;

/**
 * Produces the same controlled problem response as the MVC boundary for authentication failures that occur in the
 * security filter chain, which runs outside Spring MVC. It never exposes the framework exception message.
 */
@Component
public class CustomAuthEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    public CustomAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ProblemDetail problem = ApiProblemFactory.create(ErrorCode.AUTHENTICATION_REQUIRED);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        OutputStream out = response.getOutputStream();
        objectMapper.writeValue(out, problem);
        out.flush();
    }
}
