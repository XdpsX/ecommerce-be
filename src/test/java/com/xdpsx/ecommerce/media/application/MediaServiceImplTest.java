package com.xdpsx.ecommerce.media.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaResourceType;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryUploadResponse;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryUploader;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MediaServiceImplTest {
    @InjectMocks
    private MediaServiceImpl mediaService;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private CloudinaryUploader cloudinaryUploader;

    @Nested
    @DisplayName("1. createMedia")
    @Order(1)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CreateMediaTests {
        CloudinaryUploadResponse uploadResponse = new CloudinaryUploadResponse("displayName", "publicId", "url");
        MediaResourceType resourceType = MediaResourceType.CATEGORY;
        int validWidth = 500;
        int invalidWidth = resourceType.minWidth() - 10;

        @DisplayName("1.1 should create media successfully")
        @Order(1)
        @Test
        void createMedia_shouldCreateSuccess() throws Exception {
            // Given
            MultipartFile mockFile = mockImageFile(validWidth);
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            when(cloudinaryUploader.uploadFile(eq(request.file()), anyMap())).thenReturn(uploadResponse);

            Media media = Media.builder()
                    .id(uploadResponse.displayName())
                    .externalId(uploadResponse.publicId())
                    .url(uploadResponse.url())
                    .caption(request.caption())
                    .contentType(mockFile.getContentType())
                    .resourceType(resourceType)
                    .tempFlg(true)
                    .deleteFlg(false)
                    .build();
            when(mediaRepository.save(any(Media.class))).thenReturn(media);

            // When
            ViewMediaDTO result = mediaService.createMedia(request, resourceType);

            // Then
            assertNotNull(result);
            assertEquals(uploadResponse.displayName(), result.id());
            assertEquals(uploadResponse.url(), result.url());
            assertEquals(request.caption(), result.caption());
            assertEquals(mockFile.getContentType(), result.contentType());

            verify(cloudinaryUploader).uploadFile(eq(mockFile), anyMap());
            verify(mediaRepository).save(any(Media.class));
        }

        @DisplayName("1.2 should create media successfully without validate size")
        @Order(2)
        @Test
        void createMedia_shouldCreateWithoutValidateSize() throws Exception {
            // Arrange
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getContentType()).thenReturn("image/jpeg");

            // Mock the getInputStream method to throw an exception
            lenient().when(mockFile.getInputStream()).thenThrow(new RuntimeException("Should not call getInputStream"));

            CreateMediaDTO request = new CreateMediaDTO(null, mockFile);

            MediaResourceType mockResourceType = mock(MediaResourceType.class);
            when(mockResourceType.minWidth()).thenReturn(null);
            when(mockResourceType.getUploadOptions()).thenReturn(Map.of());

            when(cloudinaryUploader.uploadFile(eq(mockFile), anyMap())).thenReturn(uploadResponse);

            when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ViewMediaDTO result = mediaService.createMedia(request, mockResourceType);

            // Assert
            assertNotNull(result);
            assertEquals(uploadResponse.url(), result.url());

            verify(cloudinaryUploader).uploadFile(eq(mockFile), anyMap());
            verify(mediaRepository).save(any(Media.class));
        }

        @DisplayName("1.3 should create media fail when image width is too small")
        @Order(3)
        @Test
        void createMedia_shouldFail_whenImageWidthTooSmall() throws Exception {
            // Given
            MultipartFile mockFile = mockImageFile(invalidWidth);
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            // When - Then
            ApplicationException ex =
                    assertThrows(ApplicationException.class, () -> mediaService.createMedia(request, resourceType));
            assertEquals(ErrorCode.INVALID_IMAGE_WIDTH, ex.getCode());
            assertEquals(resourceType.minWidth(), ex.getParameters().get("minWidth"));
        }

        @DisplayName("1.4 should create media fail when upload file failed")
        @Order(4)
        @Test
        void createMedia_shouldFail_whenUploadCloudinaryFails() throws Exception {
            // Given
            MultipartFile mockFile = mockImageFile(validWidth);
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            when(cloudinaryUploader.uploadFile(any(MultipartFile.class), anyMap()))
                    .thenThrow(new RuntimeException("Upload failed"));

            // When - Then
            ApplicationException ex =
                    assertThrows(ApplicationException.class, () -> mediaService.createMedia(request, resourceType));
            assertEquals(ErrorCode.MEDIA_UPLOAD_FAILED, ex.getCode());
            // The provider failure message must not leak into the exposed message.
            assertFalse(ex.getMessage().contains("Upload failed"));
            assertNotNull(ex.getCause());
        }

        @DisplayName("1.5 should throw exception when image is not valid")
        @Order(5)
        @Test
        void validateImageSize_ShouldThrowIllegalArgumentException_WhenImageIsNull() throws IOException {
            // Arrange
            MultipartFile mockFile = mock(MultipartFile.class);
            ByteArrayInputStream invalidStream = new ByteArrayInputStream("not-an-image".getBytes());
            when(mockFile.getInputStream()).thenReturn(invalidStream);

            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            // Act & Assert
            IllegalArgumentException exception =
                    assertThrows(IllegalArgumentException.class, () -> mediaService.createMedia(request, resourceType));

            assertEquals("Invalid image format", exception.getMessage());
        }

        @DisplayName("1.6 should throw exception when can not read image")
        @Order(6)
        @Test
        void validateImageSize_ShouldThrowRuntimeException_WhenIOExceptionOccurs() throws IOException {
            // Arrange
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getInputStream()).thenThrow(new IOException("Stream error"));

            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            // Act & Assert
            RuntimeException exception =
                    assertThrows(RuntimeException.class, () -> mediaService.createMedia(request, resourceType));

            assertEquals("Failed to read image file", exception.getMessage());
            assertInstanceOf(IOException.class, exception.getCause());
        }

        @DisplayName("1.7 should delete uploaded file and propagate original failure when saving media fails")
        @Order(7)
        @Test
        void createMedia_ShouldDeleteUploadedFile_WhenSavingMediaFails() throws Exception {
            // Arrange
            MultipartFile mockFile = mockImageFile(validWidth);

            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            when(cloudinaryUploader.uploadFile(eq(mockFile), anyMap())).thenReturn(uploadResponse);

            // Simulate save fails
            when(mediaRepository.save(any(Media.class))).thenThrow(new RuntimeException("DB error"));

            // Act & Assert
            // A persistence failure is NOT an upload failure: the original exception propagates
            // (surfaced as INTERNAL_ERROR at the API boundary), and the uploaded file is cleaned up.
            RuntimeException exception =
                    assertThrows(RuntimeException.class, () -> mediaService.createMedia(request, resourceType));

            assertFalse(exception instanceof ApplicationException);
            assertEquals("DB error", exception.getMessage());

            verify(cloudinaryUploader).deleteFile(uploadResponse.publicId());
            verify(cloudinaryUploader).uploadFile(eq(mockFile), anyMap());
            verify(mediaRepository).save(any(Media.class));
        }
    }

    @Nested
    @DisplayName("2. deleteMedia")
    @Order(2)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class DeleteMediaTests {
        String mediaId = "mediaId";

        @DisplayName("2.1 should delete media successfully")
        @Order(1)
        @Test
        void deleteMedia_ShouldSetDeleteFlagAndSave_WhenMediaExists() {
            // Arrange
            Media media = Media.builder().id(mediaId).deleteFlg(false).build();

            when(mediaRepository.findPublicMediaById(mediaId)).thenReturn(Optional.of(media));

            // Act
            mediaService.deleteMedia(mediaId);

            // Assert
            assertTrue(media.isDeleteFlg());
            verify(mediaRepository).save(media);
        }

        @DisplayName("2.2 should throw ApplicationException(RESOURCE_NOT_FOUND) when media does not exist")
        @Order(2)
        @Test
        void deleteMedia_ShouldThrowResourceNotFound_WhenMediaDoesNotExist() {
            // Arrange
            when(mediaRepository.findPublicMediaById(mediaId)).thenReturn(Optional.empty());

            // Act & Assert
            ApplicationException exception =
                    assertThrows(ApplicationException.class, () -> mediaService.deleteMedia(mediaId));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
            assertEquals("media", exception.getParameters().get("resourceType"));
            assertEquals(mediaId, exception.getParameters().get("resourceId"));
            verify(mediaRepository, never()).save(any());
        }
    }

    // Helper methods

    private MultipartFile mockImageFile(int width) throws IOException {
        BufferedImage img = new BufferedImage(width, 500, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        baos.flush();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(bais);
        lenient().when(file.getContentType()).thenReturn("image/jpeg");
        return file;
    }
}
