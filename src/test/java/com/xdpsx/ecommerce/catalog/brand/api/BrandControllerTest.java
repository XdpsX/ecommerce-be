package com.xdpsx.ecommerce.catalog.brand.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

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

import com.xdpsx.ecommerce.catalog.brand.api.dto.AdminBrandFilter;
import com.xdpsx.ecommerce.catalog.brand.api.dto.AdminBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.application.BrandService;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;

@WebMvcTest(controllers = AdminBrandController.class)
@Import({BrandControllerTest.MethodSecurityConfig.class, BrandControllerTest.TestSecurityConfig.class})
class BrandControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {}

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    @Test
    void getAdminBrands_ShouldReturnPageResponse_ForAdmin() throws Exception {
        when(brandService.getAdminBrands(any(AdminBrandFilter.class)))
                .thenReturn(PageResponse.of(List.of(), 1, 10, 0, 0));

        mockMvc.perform(get("/admin/brands").with(admin()).param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.page").value(1));
    }

    @Test
    void getAdminBrands_ShouldRejectInvalidSort() throws Exception {
        mockMvc.perform(get("/admin/brands").with(admin()).param("sort", "unknown"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(brandService);
    }

    @Test
    void adminBoundary_ShouldRejectAnonymousAndNonAdminCallers() throws Exception {
        mockMvc.perform(get("/admin/brands/100")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/brands/100").with(user("customer").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/brands")
                        .with(user("customer").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nike\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(brandService);
    }

    @Test
    void getAdminBrand_ShouldReturnStatusVersionAndAssociations() throws Exception {
        ViewMediaDTO image = new ViewMediaDTO("logo-1", "Nike logo", "image/png", "https://example.test/logo.png");
        AdminBrandResponse response = new AdminBrandResponse(
                100, "Nike", BrandStatus.ACTIVE, 4L, image, List.of(new AdminBrandResponse.CategoryDTO(1, "Shoes")));
        when(brandService.getAdminBrand(anyInt())).thenReturn(response);

        mockMvc.perform(get("/admin/brands/100").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.categories[0].name").value("Shoes"))
                .andExpect(jsonPath("$.image.url").value("https://example.test/logo.png"));
    }

    @Test
    void legacyRoutes_ShouldNotBeExposed() throws Exception {
        mockMvc.perform(post("/brands/create")).andExpect(status().isNotFound());
        mockMvc.perform(put("/brands/100/update")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/100/delete")).andExpect(status().isNotFound());
        mockMvc.perform(post("/brands/exists")).andExpect(status().isNotFound());
    }

    @Test
    void createBrand_ShouldReturnCreatedLocation_ForAdmin() throws Exception {
        AdminBrandResponse response = new AdminBrandResponse(101, "Adidas", BrandStatus.ACTIVE, 0L, null, List.of());
        when(brandService.createBrand(any())).thenReturn(response);

        mockMvc.perform(post("/admin/brands")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Adidas\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/admin/brands/101"))
                .andExpect(jsonPath("$.version").value(0));
        verify(brandService).createBrand(any());
    }
}
