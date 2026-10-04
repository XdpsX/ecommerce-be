package com.xdpsx.ecommerce.inventory.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xdpsx.ecommerce.inventory.api.dto.InventoryBalanceResponse;
import com.xdpsx.ecommerce.inventory.application.InventoryService;

@WebMvcTest(controllers = AdminInventoryController.class)
@Import({AdminInventoryControllerTest.MethodSecurityConfig.class, AdminInventoryControllerTest.TestSecurityConfig.class
})
class AdminInventoryControllerTest {
    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {}

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.requestMatchers("/admin/inventory/**")
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
    private InventoryService inventoryService;

    @Test
    void adminEndpoints_ShouldRequireAdminAndUseAuthenticatedActor() throws Exception {
        mockMvc.perform(get("/admin/inventory/variants/10")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/inventory/variants/10")
                        .with(user("customer").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventoryService);

        when(inventoryService.getBalance(10L)).thenReturn(new InventoryBalanceResponse(10L, "SKU-10", 7, 0, 7));
        mockMvc.perform(get("/admin/inventory/variants/10").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-10"))
                .andExpect(jsonPath("$.available").value(7));
        verify(inventoryService).getBalance(10L);
    }

    @Test
    void adjustment_ShouldValidateDeltaAndForwardAuthenticatedName() throws Exception {
        when(inventoryService.adjustOnHand(any(), any(), any()))
                .thenReturn(new InventoryBalanceResponse(10L, "SKU-10", 7, 0, 7));

        mockMvc.perform(post("/admin/inventory/variants/10/adjustments")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityDelta\":0,\"reason\":\"receipt\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantityDelta"));
        verifyNoInteractions(inventoryService);

        mockMvc.perform(post("/admin/inventory/variants/10/adjustments")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityDelta\":7,\"reason\":\" receipt \"}"))
                .andExpect(status().isOk());
        verify(inventoryService)
                .adjustOnHand(
                        10L,
                        new com.xdpsx.ecommerce.inventory.api.dto.InventoryAdjustmentRequest(7L, " receipt "),
                        "admin@example.test");
    }
}
