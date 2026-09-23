package com.xdpsx.ecommerce.catalog.brand.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xdpsx.ecommerce.catalog.brand.api.dto.*;
import com.xdpsx.ecommerce.catalog.brand.application.BrandService;
import com.xdpsx.ecommerce.catalog.shared.api.dto.CheckExistResponse;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.error.SMessage;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.testsupport.SecurityConfigForControllerTests;

@WebMvcTest(controllers = BrandController.class)
@Import(SecurityConfigForControllerTests.class)
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BrandService brandService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAdminBrands_shouldReturnWrappedApiResponse() throws Exception {
        // Arrange
        PageResponse<AdminBrandResponse> mockPage = PageResponse.<AdminBrandResponse>builder()
                .items(Collections.emptyList())
                .pageNum(1)
                .pageSize(10)
                .totalItems(0)
                .totalPages(0)
                .build();

        Mockito.when(brandService.getAdminBrands(any(AdminBrandFilter.class))).thenReturn(mockPage);

        // Act & Assert
        mockMvc.perform(get("/admin/brands")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value(SMessage.SUCCESS.message()))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void getAdminBrandDetail_shouldReturnWrappedBrandDetail() throws Exception {
        // Arrange
        ViewMediaDTO media =
                new ViewMediaDTO("media-123", "Brand logo", "image/png", "http://example.com/media/brand.png");

        BrandDetailResponse.CategoryDTO category1 = new BrandDetailResponse.CategoryDTO(1, "Electronics");
        BrandDetailResponse.CategoryDTO category2 = new BrandDetailResponse.CategoryDTO(2, "Fashion");

        BrandDetailResponse response = new BrandDetailResponse(100, "Nike", true, media, List.of(category1, category2));

        Mockito.when(brandService.getAdminBrandDetail(anyInt())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/admin/brands/100").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value(SMessage.SUCCESS.message()))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.name").value("Nike"))
                .andExpect(jsonPath("$.data.publicFlg").value(true))
                .andExpect(jsonPath("$.data.image.id").value("media-123"))
                .andExpect(jsonPath("$.data.image.caption").value("Brand logo"))
                .andExpect(jsonPath("$.data.image.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.image.url").value("http://example.com/media/brand.png"))
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.categories[0].id").value(1))
                .andExpect(jsonPath("$.data.categories[0].name").value("Electronics"))
                .andExpect(jsonPath("$.data.categories[1].id").value(2))
                .andExpect(jsonPath("$.data.categories[1].name").value("Fashion"));
    }

    @Test
    void createBrand_shouldReturnCreatedBrand() throws Exception {
        // Arrange
        CreateBrandRequest request = new CreateBrandRequest("Adidas", true, "media-123", Set.of(1, 2));
        BrandDetailResponse response = new BrandDetailResponse(
                101,
                "Adidas",
                true,
                new ViewMediaDTO("media-123", "Brand logo", "image/png", "http://example.com/media/adidas.png"),
                List.of(
                        new BrandDetailResponse.CategoryDTO(1, "Electronics"),
                        new BrandDetailResponse.CategoryDTO(2, "Fashion")));

        Mockito.when(brandService.createBrand(any(CreateBrandRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/brands/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value(SMessage.CREATE_SUCCESSFULLY.message()))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.name").value("Adidas"));
    }

    @Test
    void updateBrand_shouldReturnUpdatedBrand() throws Exception {
        // Arrange
        UpdateBrandRequest request =
                new UpdateBrandRequest("Adidas Updated", true, "media-456", Set.of(1), LocalDateTime.now());
        BrandDetailResponse response = new BrandDetailResponse(
                101,
                "Adidas Updated",
                true,
                new ViewMediaDTO(
                        "media-456", "Updated logo", "image/png", "http://example.com/media/adidas-updated.png"),
                List.of(new BrandDetailResponse.CategoryDTO(1, "Electronics")));

        Mockito.when(brandService.updateBrand(anyInt(), any(UpdateBrandRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(put("/brands/101/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value(SMessage.SUCCESS.message()))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.name").value("Adidas Updated"));
    }

    @Test
    void deleteBrand_shouldReturnNoContent() throws Exception {
        // Arrange
        ModifyExclusiveDTO request = new ModifyExclusiveDTO(LocalDateTime.now());

        Mockito.doNothing().when(brandService).deleteBrand(anyInt(), any(ModifyExclusiveDTO.class));

        // Act & Assert
        mockMvc.perform(delete("/101/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value(SMessage.DELETE_SUCCESSFULLY.message()));
    }

    @Test
    void checkBrandExist_shouldReturnExistenceStatus() throws Exception {
        // Arrange
        BrandExistRequest request = new BrandExistRequest("Nike");
        CheckExistResponse response = new CheckExistResponse("name", true);

        Mockito.when(brandService.checkBrandExist(any(BrandExistRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/brands/exists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value(SMessage.SUCCESS.message()))
                .andExpect(jsonPath("$.data.field").value("name"))
                .andExpect(jsonPath("$.data.exists").value(true));
    }
}
