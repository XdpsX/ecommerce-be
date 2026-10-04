package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Transformation;
import com.xdpsx.ecommerce.media.application.storage.MediaStorageException;
import com.xdpsx.ecommerce.media.application.storage.MediaUploadCommand;
import com.xdpsx.ecommerce.media.application.storage.StoredMedia;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

class CloudinaryMediaStorageTest {

    private CloudinaryUploader cloudinaryUploader;
    private CloudinaryMediaStorage mediaStorage;
    private MultipartFile file;

    @BeforeEach
    void setUp() {
        cloudinaryUploader = mock(CloudinaryUploader.class);
        mediaStorage = new CloudinaryMediaStorage(cloudinaryUploader);
        file = mock(MultipartFile.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void upload_ShouldMapPurposeToFolderAndTransformation_AndPreferSecureUrl() {
        // Arrange
        CloudinaryUploadResponse response = new CloudinaryUploadResponse(
                "brands/logo-id",
                "http://insecure",
                "https://secure",
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0L,
                "display",
                null,
                null);
        when(cloudinaryUploader.uploadFile(eq(file), anyMap())).thenReturn(response);

        // Act
        StoredMedia storedMedia = mediaStorage.upload(new MediaUploadCommand(file, MediaPurpose.BRAND_LOGO));

        // Assert
        assertEquals("brands/logo-id", storedMedia.externalId());
        assertEquals("https://secure", storedMedia.url());

        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cloudinaryUploader).uploadFile(eq(file), optionsCaptor.capture());
        Map<String, Object> options = optionsCaptor.getValue();
        assertEquals("brands", options.get("folder"));
        Transformation<?> transformation = (Transformation<?>) options.get("transformation");
        assertEquals(
                new Transformation<>()
                        .width(MediaPurpose.BRAND_LOGO.minWidth())
                        .crop("scale")
                        .generate(),
                transformation.generate());
    }

    @Test
    void upload_ShouldFallBackToPlainUrl_WhenSecureUrlIsMissing() {
        // Arrange
        CloudinaryUploadResponse response = new CloudinaryUploadResponse(
                "publicId", "http://plain-url", null, null, null, null, null, null, 0, 0, 0L, "display", null, null);
        when(cloudinaryUploader.uploadFile(eq(file), anyMap())).thenReturn(response);

        // Act
        StoredMedia storedMedia = mediaStorage.upload(new MediaUploadCommand(file, MediaPurpose.PRODUCT_IMAGE));

        // Assert
        assertEquals("publicId", storedMedia.externalId());
        assertEquals("http://plain-url", storedMedia.url());
    }

    @Test
    void upload_ShouldFail_WhenProviderResponseMissesIdentity() {
        // Arrange
        CloudinaryUploadResponse response = new CloudinaryUploadResponse(
                "  ", "http://plain-url", null, null, null, null, null, null, 0, 0, 0L, "display", null, null);
        when(cloudinaryUploader.uploadFile(eq(file), anyMap())).thenReturn(response);

        // Act + Assert
        assertThrows(
                MediaStorageException.class,
                () -> mediaStorage.upload(new MediaUploadCommand(file, MediaPurpose.PRODUCT_IMAGE)));
    }

    @Test
    void upload_ShouldFail_WhenProviderReturnsNullResponse() {
        // Arrange
        when(cloudinaryUploader.uploadFile(eq(file), anyMap())).thenReturn(null);

        // Act + Assert
        // A null provider response must surface as MediaStorageException (not NullPointerException) so the
        // API boundary keeps reporting MEDIA_UPLOAD_FAILED.
        assertThrows(
                MediaStorageException.class,
                () -> mediaStorage.upload(new MediaUploadCommand(file, MediaPurpose.PRODUCT_IMAGE)));
    }

    @Test
    void upload_ShouldRemoveUploadedAsset_WhenResponseHasIdentityButNoUrl() {
        // Arrange
        CloudinaryUploadResponse response = new CloudinaryUploadResponse(
                "orphan-id", null, null, null, null, null, null, null, 0, 0, 0L, "display", null, null);
        when(cloudinaryUploader.uploadFile(eq(file), anyMap())).thenReturn(response);
        when(cloudinaryUploader.deleteFile("orphan-id")).thenReturn(true);

        // Act + Assert
        assertThrows(
                MediaStorageException.class,
                () -> mediaStorage.upload(new MediaUploadCommand(file, MediaPurpose.PRODUCT_IMAGE)));

        // The asset exists at the provider but cannot be referenced, so it must not be left orphaned.
        verify(cloudinaryUploader).deleteFile("orphan-id");
    }

    @Test
    void upload_ShouldStillFail_WhenOrphanCleanupFails() {
        // Arrange
        CloudinaryUploadResponse response = new CloudinaryUploadResponse(
                "orphan-id", null, null, null, null, null, null, null, 0, 0, 0L, "display", null, null);
        when(cloudinaryUploader.uploadFile(eq(file), anyMap())).thenReturn(response);
        when(cloudinaryUploader.deleteFile("orphan-id")).thenThrow(new RuntimeException("cleanup down"));

        // Act + Assert
        // Cleanup failure must not mask the upload failure.
        assertThrows(
                MediaStorageException.class,
                () -> mediaStorage.upload(new MediaUploadCommand(file, MediaPurpose.PRODUCT_IMAGE)));
    }

    @Test
    void upload_ShouldWrapProviderFailure() {
        // Arrange
        when(cloudinaryUploader.uploadFile(eq(file), anyMap())).thenThrow(new RuntimeException("provider down"));

        // Act
        MediaStorageException exception = assertThrows(
                MediaStorageException.class,
                () -> mediaStorage.upload(new MediaUploadCommand(file, MediaPurpose.PRODUCT_IMAGE)));

        // Assert
        assertEquals("provider down", exception.getCause().getMessage());
    }

    @Test
    void delete_ShouldSucceed_WhenUploaderConfirmsDeletion() {
        // Arrange
        when(cloudinaryUploader.deleteFile("publicId")).thenReturn(true);

        // Act + Assert
        assertDoesNotThrow(() -> mediaStorage.delete("publicId"));
    }

    @Test
    void delete_ShouldFail_WhenUploaderCouldNotConfirmDeletion() {
        // Arrange
        when(cloudinaryUploader.deleteFile("publicId")).thenReturn(false);

        // Act + Assert
        assertThrows(MediaStorageException.class, () -> mediaStorage.delete("publicId"));
    }
}
