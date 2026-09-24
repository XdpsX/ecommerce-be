package com.xdpsx.ecommerce.catalog.brand.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xdpsx.ecommerce.catalog.brand.api.dto.*;
import com.xdpsx.ecommerce.catalog.brand.application.BrandService;
import com.xdpsx.ecommerce.catalog.shared.api.dto.CheckExistResponse;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.testsupport.SecurityConfigForControllerTests;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = BrandController.class)
@Import(SecurityConfigForControllerTests.class)
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandService brandService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAdminBrands_shouldReturnPageResponse() throws Exception {
        // Arrange
        PageResponse<AdminBrandResponse> mockPage = PageResponse.of(List.of(), 1, 10, 0, 0);

        Mockito.when(brandService.getAdminBrands(any(AdminBrandFilter.class))).thenReturn(mockPage);

        // Act & Assert
        mockMvc.perform(get("/admin/brands")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.totalElements").value(0))
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    void getAdminBrandDetail_shouldReturnBrandDetail() throws Exception {
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
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("Nike"))
                .andExpect(jsonPath("$.publicFlg").value(true))
                .andExpect(jsonPath("$.image.id").value("media-123"))
                .andExpect(jsonPath("$.image.caption").value("Brand logo"))
                .andExpect(jsonPath("$.image.contentType").value("image/png"))
                .andExpect(jsonPath("$.image.url").value("http://example.com/media/brand.png"))
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.categories[0].id").value(1))
                .andExpect(jsonPath("$.categories[0].name").value("Electronics"))
                .andExpect(jsonPath("$.categories[1].id").value(2))
                .andExpect(jsonPath("$.categories[1].name").value("Fashion"));
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
                .andExpect(header().string("Location", "/admin/brands/101"))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.name").value("Adidas"));
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
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.name").value("Adidas Updated"));
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
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
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
                .andExpect(jsonPath("$.field").value("name"))
                .andExpect(jsonPath("$.exists").value(true));
    }
}
