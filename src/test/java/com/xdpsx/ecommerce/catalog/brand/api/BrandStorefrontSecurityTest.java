package com.xdpsx.ecommerce.catalog.brand.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xdpsx.ecommerce.catalog.brand.api.dto.StorefrontBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.application.BrandService;

@WebMvcTest(controllers = {StorefrontBrandController.class, AdminBrandController.class})
@Import({BrandStorefrontSecurityTest.MethodSecurityConfig.class, BrandStorefrontSecurityTest.TestSecurityConfig.class})
class BrandStorefrontSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {}

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.requestMatchers("/admin/brands", "/admin/brands/**")
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
    private BrandService brandService;

    @Test
    void storefrontList_ShouldBePublicAndExposeOnlyReadModelFields() throws Exception {
        when(brandService.getStorefrontBrands(null))
                .thenReturn(List.of(new StorefrontBrandResponse(3, "Nike", "https://example.test/nike.png")));

        mockMvc.perform(get("/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("Nike"))
                .andExpect(jsonPath("$[0].image").value("https://example.test/nike.png"))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].version").doesNotExist())
                .andExpect(jsonPath("$[0].categories").doesNotExist());

        verify(brandService).getStorefrontBrands(null);
    }

    @Test
    void storefrontList_ShouldPassCategoryFilter() throws Exception {
        when(brandService.getStorefrontBrands(12)).thenReturn(List.of());

        mockMvc.perform(get("/brands").param("categoryId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(brandService).getStorefrontBrands(12);
    }

    @Test
    void adminBoundary_ShouldRemainProtectedAlongsidePublicRead() throws Exception {
        mockMvc.perform(get("/admin/brands/3")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/brands/3").with(user("customer").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(brandService);
    }
}
