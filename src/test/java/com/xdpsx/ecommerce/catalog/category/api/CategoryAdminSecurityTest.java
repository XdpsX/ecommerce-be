package com.xdpsx.ecommerce.catalog.category.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.xdpsx.ecommerce.catalog.category.api.dto.*;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.testsupport.SecurityConfigForControllerTests;

import tools.jackson.databind.ObjectMapper;

/**
 * Guards the split between the public storefront reads and the admin write boundary.
 *
 * <p>{@code hasRole('ADMIN')} only takes effect when method security is enabled, so this slice enables it
 * explicitly and uses the MockMvc security post-processor, which binds the test authentication to the request
 * before the security filter chain runs.
 */
@WebMvcTest(controllers = {AdminCategoryController.class, StorefrontCategoryController.class})
@Import({SecurityConfigForControllerTests.class, CategoryAdminSecurityTest.MethodSecurityConfig.class})
class CategoryAdminSecurityTest {

    @org.springframework.boot.test.context.TestConfiguration
    // CGLIB proxying must match the production configuration; with interface-based proxying the admin controller
    // loses @RestController and its routes are never registered.
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String CREATE_BODY = """
		{"name":"Laptops","status":"ACTIVE"}
	""";

    private RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private RequestPostProcessor normalUser() {
        return user("customer").roles("USER");
    }

    private String updateBody() throws Exception {
        return objectMapper.writeValueAsString(
                new UpdateCategoryRequest("Laptops", CategoryStatus.ACTIVE, null, null, LocalDateTime.now()));
    }

    private String deleteBody() throws Exception {
        return objectMapper.writeValueAsString(new ModifyExclusiveDTO(LocalDateTime.now()));
    }

    private String moveBody() throws Exception {
        return objectMapper.writeValueAsString(new MoveCategoryRequest(7, 2));
    }

    private String reorderBody() throws Exception {
        return objectMapper.writeValueAsString(new ReorderCategoriesRequest(null, List.of(3, 1, 2)));
    }

    @Test
    void storefrontTree_ShouldBePublic() throws Exception {
        when(categoryService.getCategoryTree(any(CategoryTreeFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void adminList_ShouldRejectUnauthenticatedCaller() throws Exception {
        // The filter chain still ends in permitAll (the repository-wide authorization baseline is a separate
        // change), so an anonymous request reaches method security and is denied there. That yields 403 rather
        // than 401; the request is rejected either way.
        mockMvc.perform(get("/admin/categories")).andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void adminList_ShouldRejectNonAdminCaller() throws Exception {
        mockMvc.perform(get("/admin/categories").with(normalUser())).andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void adminList_ShouldReachService_ForAdminCaller() throws Exception {
        when(categoryService.getAdminCategories(any(AdminCategoryFilter.class)))
                .thenReturn(PageResponse.of(List.of(), 1, 10, 0, 0));

        mockMvc.perform(get("/admin/categories").with(admin())).andExpect(status().isOk());
    }

    @Test
    void adminCategoryDetail_ShouldRejectUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/admin/categories/1")).andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void createCategory_ShouldRejectNonAdminCaller() throws Exception {
        mockMvc.perform(post("/admin/categories")
                        .with(normalUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void updateCategory_ShouldRejectNonAdminCaller() throws Exception {
        mockMvc.perform(put("/admin/categories/1")
                        .with(normalUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void moveCategory_ShouldRejectUnauthenticatedCaller() throws Exception {
        mockMvc.perform(put("/admin/categories/1/parent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void moveCategory_ShouldRejectNonAdminCaller() throws Exception {
        mockMvc.perform(put("/admin/categories/1/parent")
                        .with(normalUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void reorderCategories_ShouldRejectNonAdminCaller() throws Exception {
        mockMvc.perform(put("/admin/categories/order")
                        .with(normalUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void moveCategory_ShouldPassRequestToService_ForAdminCaller() throws Exception {
        AdminCategoryResponse response = new AdminCategoryResponse(
                3,
                "Laptops",
                "laptops",
                CategoryStatus.ACTIVE,
                2,
                null,
                new AdminCategoryResponse.CategoryDTO(7, "Electronics"));
        when(categoryService.moveCategory(anyInt(), any(MoveCategoryRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/admin/categories/3/parent")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayOrder").value(2))
                .andExpect(jsonPath("$.parent.id").value(7));
    }

    @Test
    void reorderCategories_ShouldReturnNoContent_ForAdminCaller() throws Exception {
        mockMvc.perform(put("/admin/categories/order")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderBody()))
                .andExpect(status().isNoContent());
    }

    @Test
    void moveCategory_ShouldRejectMissingPosition() throws Exception {
        mockMvc.perform(put("/admin/categories/3/parent")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":7}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    void moveCategory_ShouldRejectNegativePosition() throws Exception {
        mockMvc.perform(put("/admin/categories/3/parent")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":-1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    void reorderCategories_ShouldRejectEmptyCategoryList() throws Exception {
        mockMvc.perform(put("/admin/categories/order")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":null,\"categoryIds\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    void reorderCategories_ShouldRejectNonPositiveCategoryId() throws Exception {
        mockMvc.perform(put("/admin/categories/order")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":null,\"categoryIds\":[0]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    void deleteCategory_ShouldRejectNonAdminCaller() throws Exception {
        mockMvc.perform(delete("/admin/categories/1")
                        .with(normalUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void createCategory_ShouldReturnCreated_WithLocationHeader() throws Exception {
        AdminCategoryResponse response =
                new AdminCategoryResponse(3, "Laptops", "laptops", CategoryStatus.ACTIVE, 2, null, null);
        when(categoryService.createCategory(any(CreateCategoryRequest.class))).thenReturn(response);

        mockMvc.perform(post("/admin/categories")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/admin/categories/3"))
                .andExpect(jsonPath("$.slug").value("laptops"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updateCategory_ShouldReturnUpdatedCategory() throws Exception {
        AdminCategoryResponse response =
                new AdminCategoryResponse(3, "Laptops", "laptops", CategoryStatus.INACTIVE, 2, null, null);
        when(categoryService.updateCategory(anyInt(), any(UpdateCategoryRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/admin/categories/3")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deleteCategory_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/admin/categories/3")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody()))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateCategory_ShouldRejectMalformedExplicitSlug() throws Exception {
        String body = """
			{"name":"Laptops","status":"ACTIVE","slug":"Not Normalized","lastRetrievedAt":"2026-01-01T00:00:00"}
		""";

        mockMvc.perform(put("/admin/categories/3")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    void updateCategory_ShouldIgnoreUnknownParentIdField() throws Exception {
        // The parent is no longer part of the update contract; an old client payload must not change the hierarchy.
        AdminCategoryResponse response =
                new AdminCategoryResponse(3, "Laptops", "laptops", CategoryStatus.ACTIVE, 2, null, null);
        when(categoryService.updateCategory(anyInt(), any(UpdateCategoryRequest.class)))
                .thenReturn(response);
        String body = """
			{"name":"Laptops","status":"ACTIVE","parentId":99,"lastRetrievedAt":"2026-01-01T00:00:00"}
		""";

        mockMvc.perform(put("/admin/categories/3")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(categoryService)
                .updateCategory(
                        eq(3),
                        eq(new UpdateCategoryRequest(
                                "Laptops",
                                CategoryStatus.ACTIVE,
                                null,
                                null,
                                LocalDateTime.parse("2026-01-01T00:00:00"))));
    }
}
