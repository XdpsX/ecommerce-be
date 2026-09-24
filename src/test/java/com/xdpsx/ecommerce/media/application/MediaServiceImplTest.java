package com.xdpsx.ecommerce.media.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.application.storage.MediaStorage;
import com.xdpsx.ecommerce.media.application.storage.MediaStorageException;
import com.xdpsx.ecommerce.media.application.storage.MediaUploadCommand;
import com.xdpsx.ecommerce.media.application.storage.StoredMedia;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {
    @InjectMocks
    private MediaServiceImpl mediaService;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private MediaStorage mediaStorage;

    @Nested
    @DisplayName("1. createMedia")
    class CreateMediaTests {
        MediaPurpose purpose = MediaPurpose.CATEGORY_IMAGE;
        StoredMedia storedMedia = new StoredMedia("categories/asset-id", "https://secure-url");
        int validWidth = 500;
        int invalidWidth = purpose.minWidth() - 10;

        @DisplayName("1.1 should create a temporary media with an application generated UUID")
        @Test
        void createMedia_shouldCreateSuccess() throws Exception {
            // Given
            MultipartFile mockFile = mockImageFile(validWidth);
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            when(mediaStorage.upload(any(MediaUploadCommand.class))).thenReturn(storedMedia);
            when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ViewMediaDTO result = mediaService.createMedia(request, purpose);

            // Then
            ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(mediaCaptor.capture());
            Media persisted = mediaCaptor.getValue();

            assertEquals(36, persisted.getId().length());
            assertNotEquals(persisted.getExternalId(), persisted.getId());
            assertEquals(storedMedia.externalId(), persisted.getExternalId());
            assertEquals(storedMedia.url(), persisted.getUrl());
            assertEquals("test caption", persisted.getCaption());
            assertEquals("image/jpeg", persisted.getContentType());
            assertEquals(purpose, persisted.getPurpose());
            assertEquals(MediaStatus.TEMPORARY, persisted.getStatus());

            assertEquals(persisted.getId(), result.id());
            assertEquals(storedMedia.url(), result.url());

            ArgumentCaptor<MediaUploadCommand> commandCaptor = ArgumentCaptor.forClass(MediaUploadCommand.class);
            verify(mediaStorage).upload(commandCaptor.capture());
            assertSame(mockFile, commandCaptor.getValue().file());
            assertEquals(purpose, commandCaptor.getValue().purpose());
        }

        @DisplayName("1.2 should fail when image width is too small")
        @Test
        void createMedia_shouldFail_whenImageWidthTooSmall() throws Exception {
            // Given
            MultipartFile mockFile = mockImageFile(invalidWidth);
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            // When - Then
            ApplicationException ex =
                    assertThrows(ApplicationException.class, () -> mediaService.createMedia(request, purpose));
            assertEquals(ErrorCode.INVALID_IMAGE_WIDTH, ex.getCode());
            assertEquals(purpose.minWidth(), ex.getParameters().get("minWidth"));
            verify(mediaStorage, never()).upload(any());
        }

        @DisplayName("1.3 should fail when storage upload fails")
        @Test
        void createMedia_shouldFail_whenStorageUploadFails() throws Exception {
            // Given
            MultipartFile mockFile = mockImageFile(validWidth);
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            when(mediaStorage.upload(any(MediaUploadCommand.class)))
                    .thenThrow(new MediaStorageException("provider rejected the upload"));

            // When - Then
            ApplicationException ex =
                    assertThrows(ApplicationException.class, () -> mediaService.createMedia(request, purpose));
            assertEquals(ErrorCode.MEDIA_UPLOAD_FAILED, ex.getCode());
            // The provider failure message must not leak into the exposed message.
            assertFalse(ex.getMessage().contains("provider rejected the upload"));
            assertNotNull(ex.getCause());
            verify(mediaRepository, never()).save(any());
        }

        @DisplayName("1.4 should throw exception when image is not valid")
        @Test
        void validateImageSize_ShouldThrowIllegalArgumentException_WhenImageIsNull() throws IOException {
            // Arrange
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("not-an-image".getBytes()));
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            // Act & Assert
            IllegalArgumentException exception =
                    assertThrows(IllegalArgumentException.class, () -> mediaService.createMedia(request, purpose));

            assertEquals("Invalid image format", exception.getMessage());
        }

        @DisplayName("1.5 should throw exception when can not read image")
        @Test
        void validateImageSize_ShouldThrowRuntimeException_WhenIOExceptionOccurs() throws IOException {
            // Arrange
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getInputStream()).thenThrow(new IOException("Stream error"));
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            // Act & Assert
            RuntimeException exception =
                    assertThrows(RuntimeException.class, () -> mediaService.createMedia(request, purpose));

            assertEquals("Failed to read image file", exception.getMessage());
            assertInstanceOf(IOException.class, exception.getCause());
        }

        @DisplayName("1.6 should delete the uploaded asset and propagate the original failure when saving fails")
        @Test
        void createMedia_ShouldDeleteUploadedFile_WhenSavingMediaFails() throws Exception {
            // Arrange
            MultipartFile mockFile = mockImageFile(validWidth);
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            when(mediaStorage.upload(any(MediaUploadCommand.class))).thenReturn(storedMedia);
            when(mediaRepository.save(any(Media.class))).thenThrow(new RuntimeException("DB error"));

            // Act & Assert
            // A persistence failure is NOT an upload failure: the original exception propagates
            // (surfaced as INTERNAL_ERROR at the API boundary), and the uploaded asset is cleaned up.
            RuntimeException exception =
                    assertThrows(RuntimeException.class, () -> mediaService.createMedia(request, purpose));

            assertFalse(exception instanceof ApplicationException);
            assertEquals("DB error", exception.getMessage());
            assertEquals(0, exception.getSuppressed().length);

            verify(mediaStorage).delete(storedMedia.externalId());
        }

        @DisplayName("1.7 should suppress compensation failure on the original persistence failure")
        @Test
        void createMedia_ShouldSuppressCleanupFailure_WhenSavingAndCleanupBothFail() throws Exception {
            // Arrange
            MultipartFile mockFile = mockImageFile(validWidth);
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);

            when(mediaStorage.upload(any(MediaUploadCommand.class))).thenReturn(storedMedia);
            when(mediaRepository.save(any(Media.class))).thenThrow(new RuntimeException("DB error"));
            doThrow(new MediaStorageException("cleanup failed"))
                    .when(mediaStorage)
                    .delete(storedMedia.externalId());

            // Act & Assert
            RuntimeException exception =
                    assertThrows(RuntimeException.class, () -> mediaService.createMedia(request, purpose));

            assertEquals("DB error", exception.getMessage());
            assertEquals(1, exception.getSuppressed().length);
            assertEquals("cleanup failed", exception.getSuppressed()[0].getMessage());
        }

        @DisplayName("1.8 should keep the persisted media when only the response mapping fails")
        @Test
        void createMedia_ShouldNotCompensate_WhenMappingFailsAfterPersist() throws Exception {
            // Arrange
            MultipartFile mockFile = mockImageFile(validWidth);
            CreateMediaDTO request = new CreateMediaDTO("test caption", mockFile);
            when(mediaStorage.upload(any(MediaUploadCommand.class))).thenReturn(storedMedia);

            // Persist succeeds, but reading the persisted entity for the response blows up.
            when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
                Media persisted = mock(Media.class);
                when(persisted.getUrl()).thenThrow(new RuntimeException("mapping error"));
                return persisted;
            });

            // Act & Assert
            assertThrows(RuntimeException.class, () -> mediaService.createMedia(request, purpose));

            // The row already references the asset: deleting the asset here would leave a dangling URL.
            // A temporary upload that is never returned is cleaned up by TTL instead.
            verify(mediaStorage, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("2. deleteMedia")
    class DeleteMediaTests {
        String mediaId = "mediaId";

        @DisplayName("2.1 should mark a temporary media as pending deletion")
        @Test
        void deleteMedia_ShouldMarkPendingDeletionAndSave_WhenTemporaryMediaExists() {
            // Arrange
            Media media =
                    Media.builder().id(mediaId).status(MediaStatus.TEMPORARY).build();
            when(mediaRepository.findByIdAndStatus(mediaId, MediaStatus.TEMPORARY))
                    .thenReturn(Optional.of(media));

            // Act
            mediaService.deleteMedia(mediaId);

            // Assert
            assertEquals(MediaStatus.PENDING_DELETE, media.getStatus());
            verify(mediaRepository).save(media);
            verify(mediaStorage, never()).delete(any());
        }

        @DisplayName("2.2 should throw RESOURCE_NOT_FOUND when no temporary media exists")
        @Test
        void deleteMedia_ShouldThrowResourceNotFound_WhenNoTemporaryMedia() {
            // Arrange
            when(mediaRepository.findByIdAndStatus(mediaId, MediaStatus.TEMPORARY))
                    .thenReturn(Optional.empty());

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
