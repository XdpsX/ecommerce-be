package com.xdpsx.ecommerce.media.api;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.UploadedMediaDTO;
import com.xdpsx.ecommerce.media.application.MediaDirectUploadService;
import com.xdpsx.ecommerce.media.application.MediaService;
import com.xdpsx.ecommerce.media.application.MediaUploadIntentService;
import com.xdpsx.ecommerce.media.application.UploadIntentView;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.testsupport.SecurityConfigForControllerTests;

@WebMvcTest(controllers = MediaController.class)
@Import(SecurityConfigForControllerTests.class)
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private MediaUploadIntentService mediaUploadIntentService;

    @MockitoBean
    private MediaDirectUploadService mediaDirectUploadService;

    private MockMultipartFile createValidImageFile(String filename, String contentType) throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);

        return new MockMultipartFile("file", filename, contentType, baos.toByteArray());
    }

    private final String validResource = MediaPurpose.PRODUCT_IMAGE.resource();

    @Test
    void createUploadIntent_ShouldReturnSignedInstructionsAndLifecycleStatus() throws Exception {
        java.time.LocalDateTime expiry = java.time.LocalDateTime.of(2026, 10, 15, 4, 0);
        when(mediaUploadIntentService.create(any()))
                .thenReturn(new UploadIntentView(
                        "media-123",
                        "product/media-123",
                        expiry,
                        MediaUploadStatus.PENDING_UPLOAD,
                        MediaProcessingStatus.PENDING,
                        MediaAttachmentStatus.TEMPORARY,
                        expiry,
                        "https://api.cloudinary.com/v1_1/test/image/upload",
                        Map.of("api_key", "public-key", "signature", "signature-value")));

        mockMvc.perform(post("/media/upload-intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"PRODUCT_IMAGE","filename":"front.png","contentType":"image/png","sizeBytes":2048}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaId").value("media-123"))
                .andExpect(jsonPath("$.upload.provider").value("CLOUDINARY"))
                .andExpect(jsonPath("$.upload.fields.signature").value("signature-value"))
                .andExpect(jsonPath("$.status.upload").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.intentExpiresAt").value("2026-10-15T04:00:00Z"))
                .andExpect(jsonPath("$.uploadAuthorizationExpiresAt").value("2026-10-15T04:00:00Z"));
    }

    @Test
    void createUploadIntent_ShouldReturnBadRequest_WhenContentTypeIsNotAllowed() throws Exception {
        when(mediaUploadIntentService.create(argThat(command -> "image/webp".equals(command.expectedContentType()))))
                .thenThrow(new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "contentType")));

        mockMvc.perform(post("/media/upload-intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"PRODUCT_IMAGE","filename":"front.webp","contentType":"image/webp","sizeBytes":2048}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.parameters.field").value("contentType"));

        verify(mediaUploadIntentService).create(argThat(command -> "image/webp".equals(command.expectedContentType())));
    }

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

            UploadedMediaDTO expectedViewMedia =
                    new UploadedMediaDTO("mediaId", "Test caption", "caption", "url", Map.of());

            when(mediaService.createMedia(any(CreateMediaDTO.class), eq(MediaPurpose.PRODUCT_IMAGE)))
                    .thenReturn(expectedViewMedia);

            // Act + Assert
            mockMvc.perform(multipart("/media/image-upload")
                            .file(validImageFile)
                            .param("resource", validResource)
                            .param("caption", "Test caption")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.caption").value("Test caption"))
                    .andExpect(jsonPath("$.url").value("url"));

            verify(mediaService).createMedia(any(CreateMediaDTO.class), eq(MediaPurpose.PRODUCT_IMAGE));
        }

        @DisplayName("1.2 should return unprocessable entity when invalid resource")
        @Order(2)
        @Test
        void createMedia_ShouldReturnUnprocessableEntity_WhenInvalidResource() throws Exception {
            // Arrange
            MockMultipartFile validImageFile = createValidImageFile("image.png", IMAGE_PNG_VALUE);

            // Act + Assert
            mockMvc.perform(multipart("/media/image-upload")
                            .file(validImageFile)
                            .param("resource", "INVALID_RESOURCE")
                            .param("caption", "Test caption")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("INVALID_MEDIA_RESOURCE_TYPE"))
                    .andExpect(jsonPath("$.parameters.resource").value("INVALID_RESOURCE"));
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
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("file"));
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
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("file"));
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
            doNothing().when(mediaDirectUploadService).delete("mediaId");

            // Act + Assert
            mockMvc.perform(delete("/media/mediaId"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(mediaDirectUploadService).delete("mediaId");
        }
    }
}
