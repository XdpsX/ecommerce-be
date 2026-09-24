package com.xdpsx.ecommerce.auth.infrastructure.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The security filter chain runs outside Spring MVC, so the entry point must produce the same controlled problem
 * contract without exposing the framework exception message.
 *
 * <p>The {@link ProblemDetailJacksonMixin} is registered explicitly to mirror Spring Boot's auto-configured
 * {@code ObjectMapper}, which is what the entry point receives at runtime; it is required to serialize the extended
 * problem properties ({@code code}, {@code parameters}).
 */
class CustomAuthEntryPointTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .build();

    private final CustomAuthEntryPoint entryPoint = new CustomAuthEntryPoint(objectMapper);

    @Test
    @DisplayName("Returns 401 AUTHENTICATION_REQUIRED without exposing the framework message")
    void commence_ShouldReturnControlledProblem() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/brands");
        request.setRequestURI("/admin/brands");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("token signature invalid: secret-key"));

        assertEquals(401, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());

        JsonNode problem = objectMapper.readTree(response.getContentAsString());
        assertEquals("AUTHENTICATION_REQUIRED", problem.path("code").asString());
        assertEquals("/admin/brands", problem.path("instance").asString());
        assertFalse(problem.path("detail").asString().contains("secret-key"));
        assertFalse(problem.path("title").asString().contains("secret-key"));
    }
}
