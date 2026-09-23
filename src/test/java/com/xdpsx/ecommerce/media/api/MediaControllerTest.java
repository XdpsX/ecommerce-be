package com.xdpsx.ecommerce.media.api;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xdpsx.ecommerce.common.error.EMessage;
import com.xdpsx.ecommerce.common.error.SMessage;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.application.MediaService;
import com.xdpsx.ecommerce.media.domain.MediaResourceType;
import com.xdpsx.ecommerce.testsupport.SecurityConfigForControllerTests;

@WebMvcTest(controllers = MediaController.class)
@Import(SecurityConfigForControllerTests.class)
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaService mediaService;

    private MockMultipartFile createValidImageFile(String filename, String contentType) throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);

        return new MockMultipartFile("file", filename, contentType, baos.toByteArray());
    }

    private final String validResource = MediaResourceType.PRODUCT.resource();

    @Nested
    @DisplayName("1. createMedia")
    @Order(1)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class createMediaTests {
        @DisplayName("1.1 should create media successfully")
        @Order(1)
        @Test
        void createMedia_ShouldReturnCreated_WhenValidRequest() throws Exception {
            // Arrange
            MockMultipartFile validImageFile = createValidImageFile("image.png", IMAGE_PNG_VALUE);

            ViewMediaDTO expectedViewMedia = new ViewMediaDTO("mediaId", "Test caption", "caption", "url");

            when(mediaService.createMedia(any(CreateMediaDTO.class), eq(MediaResourceType.PRODUCT)))
                    .thenReturn(expectedViewMedia);

            // Act + Assert
            mockMvc.perform(multipart("/media/image-upload")
                            .file(validImageFile)
                            .param("resource", validResource)
                            .param("caption", "Test caption")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.message").value(SMessage.CREATE_SUCCESSFULLY.message()))
                    .andExpect(jsonPath("$.data.caption").value("Test caption"))
                    .andExpect(jsonPath("$.data.url").value("url"));

            verify(mediaService).createMedia(any(CreateMediaDTO.class), eq(MediaResourceType.PRODUCT));
        }

        @DisplayName("1.2 should return bad request when invalid resource")
        @Order(2)
        @Test
        void createMedia_ShouldReturnBadRequest_WhenInvalidResource() throws Exception {
            // Arrange
            MockMultipartFile validImageFile = createValidImageFile("image.png", IMAGE_PNG_VALUE);

            // Act + Assert
            mockMvc.perform(multipart("/media/image-upload")
                            .file(validImageFile)
                            .param("resource", "INVALID_RESOURCE")
                            .param("caption", "Test caption")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(EMessage.INVALID_RESOURCE_TYPE.message()))
                    .andExpect(jsonPath("$.args[0]").value("INVALID_RESOURCE"));
        }

        @DisplayName("1.3 should return bad request when invalid file type")
        @Order(3)
        @Test
        void createMedia_ShouldReturnBadRequest_WhenInvalidFileContentType() throws Exception {
            // Arrange
            MockMultipartFile validImageFile = createValidImageFile("image.png", "INVALID_CONTENT_TYPE");

            // Act + Assert
            mockMvc.perform(multipart("/media/image-upload")
                            .file(validImageFile)
                            .param("resource", validResource)
                            .param("caption", "Test caption")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(EMessage.BAD_VALIDATION.message()));
        }

        @DisplayName("1.4 should return bad request when file is null")
        @Order(4)
        @Test
        void createMedia_ShouldReturnBadRequest_WhenFileIsNull() throws Exception {
            // Act + Assert
            mockMvc.perform(multipart("/media/image-upload")
                            .param("resource", validResource)
                            .param("caption", "Test caption")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(EMessage.BAD_VALIDATION.message()));
        }
    }

    @Nested
    @DisplayName("2. deleteMedia")
    @Order(1)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class deleteMediaTests {
        @DisplayName("1.1 should delete media successfully")
        @Order(1)
        @Test
        void deleteMedia_ShouldReturnNoContent_WhenValidId() throws Exception {
            // Arrange
            doNothing().when(mediaService).deleteMedia("mediaId");

            // Act + Assert
            mockMvc.perform(delete("/media/mediaId"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value(SMessage.DELETE_SUCCESSFULLY.message()))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(mediaService).deleteMedia("mediaId");
        }
    }
}
