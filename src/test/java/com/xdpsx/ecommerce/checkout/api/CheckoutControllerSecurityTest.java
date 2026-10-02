package com.xdpsx.ecommerce.checkout.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xdpsx.ecommerce.checkout.api.dto.CheckoutResponse;
import com.xdpsx.ecommerce.checkout.application.CheckoutService;

@WebMvcTest(controllers = CheckoutController.class)
@Import(CheckoutControllerSecurityTest.TestSecurityConfig.class)
class CheckoutControllerSecurityTest {
    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.requestMatchers("/checkout")
                            .authenticated()
                            .anyRequest()
                            .permitAll())
                    .exceptionHandling(exceptions ->
                            exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutService checkoutService;

    @Test
    void checkout_ShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/checkout")
                        .header("Idempotency-Key", "checkout-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":10}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(checkoutService);
    }

    @Test
    void checkout_ShouldRequireIdempotencyKeyAndForwardAuthenticatedCustomer() throws Exception {
        mockMvc.perform(post("/checkout")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":10}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(checkoutService);

        when(checkoutService.checkout(eq("customer@example.test"), any(), eq("checkout-1"), any()))
                .thenReturn(CheckoutResponse.builder().replayed(false).build());
        mockMvc.perform(post("/checkout")
                        .with(user("customer@example.test").roles("USER"))
                        .header("Idempotency-Key", "checkout-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":10}"))
                .andExpect(status().isCreated());
        verify(checkoutService).checkout(eq("customer@example.test"), any(), eq("checkout-1"), any());
    }
}
