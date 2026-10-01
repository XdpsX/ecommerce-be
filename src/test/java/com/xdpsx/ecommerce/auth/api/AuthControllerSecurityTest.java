package com.xdpsx.ecommerce.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xdpsx.ecommerce.auth.application.AuthService;
import com.xdpsx.ecommerce.auth.application.AuthenticatedSession;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.config.AuthSessionProperties;
import com.xdpsx.ecommerce.testsupport.SecurityConfigForControllerTests;

@WebMvcTest(AuthController.class)
@Import({
    SecurityConfigForControllerTests.class,
    RefreshCookieService.class,
    RefreshRequestGuard.class,
    AuthControllerSecurityTest.PropertiesConfig.class
})
class AuthControllerSecurityTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String REQUEST =
            "{\"name\":\"Customer\",\"email\":\"user@example.com\"," + "\"password\":\"password123\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void register_ShouldReturnAccessTokenAndHttpOnlyHostOnlyCookie() throws Exception {
        when(authService.register(any())).thenReturn(session());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshCredential").doesNotExist())
                .andExpect(header().string(
                                "Set-Cookie",
                                org.hamcrest.Matchers.allOf(
                                        org.hamcrest.Matchers.containsString("refresh_session="),
                                        org.hamcrest.Matchers.containsString("Path=/auth"),
                                        org.hamcrest.Matchers.containsString("HttpOnly"),
                                        org.hamcrest.Matchers.containsString("SameSite=Strict"),
                                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Domain=")))));
    }

    @Test
    void refreshAndLogout_ShouldRequireDedicatedHeaderBeforeCallingService() throws Exception {
        mockMvc.perform(post("/auth/refresh")).andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/logout")).andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    @Test
    void refresh_ShouldExposeOneGenericUnauthorizedFailure() throws Exception {
        when(authService.refresh(null)).thenThrow(new ApplicationException(ErrorCode.INVALID_REFRESH_CREDENTIAL));

        mockMvc.perform(post("/auth/refresh").header("X-Session-Request", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_CREDENTIAL"));
    }

    @Test
    void logout_ShouldReturnNoContentAndExpireCookie() throws Exception {
        mockMvc.perform(post("/auth/logout").header("X-Session-Request", "1"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                                "Set-Cookie",
                                org.hamcrest.Matchers.allOf(
                                        org.hamcrest.Matchers.containsString("refresh_session="),
                                        org.hamcrest.Matchers.containsString("Max-Age=0"),
                                        org.hamcrest.Matchers.containsString("Path=/auth"),
                                        org.hamcrest.Matchers.containsString("HttpOnly"))));
    }

    private static AuthenticatedSession session() {
        return new AuthenticatedSession(
                "access-token",
                "00000000-0000-0000-0000-000000000001.abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG",
                NOW.plusSeconds(1800));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PropertiesConfig {
        @Bean
        AuthSessionProperties authSessionProperties() {
            AuthSessionProperties properties = new AuthSessionProperties();
            properties.setRefreshCookieSecure(false);
            return properties;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
