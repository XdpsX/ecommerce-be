package com.xdpsx.ecommerce.auth.infrastructure.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.xdpsx.ecommerce.auth.infrastructure.security.CustomUserDetails;
import com.xdpsx.ecommerce.auth.infrastructure.security.TokenProvider;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;

class CustomAuthenticationSuccessHandlerTest {
    @Test
    void oauthSuccess_ShouldKeepRedirectFlowWithoutLocalRefreshCookie() throws Exception {
        TokenProvider tokenProvider = Mockito.mock(TokenProvider.class);
        CustomAuthenticationSuccessHandler handler = new CustomAuthenticationSuccessHandler(tokenProvider);
        org.springframework.test.util.ReflectionTestUtils.setField(
                handler, "redirectUri", "http://localhost:3001/oauth2/redirect");
        CustomUserDetails principal = CustomUserDetails.builder()
                .username("google@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .role(Role.USER)
                .build();
        when(tokenProvider.generateToken(principal)).thenReturn("oauth-access-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3001/oauth2/redirect?token=oauth-access-token");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }
}
