package com.xdpsx.ecommerce.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.Url;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xdpsx.ecommerce.dtos.media.CloudinaryUploadResponse;

class CloudinaryUploaderTest {

    private Cloudinary cloudinary;
    private ObjectMapper objectMapper;
    private CloudinaryUploader cloudinaryUploader;

    @BeforeEach
    void setUp() {
        cloudinary = mock(Cloudinary.class);
        objectMapper = mock(ObjectMapper.class);
        cloudinaryUploader = new CloudinaryUploader(cloudinary, objectMapper);
    }

    @Test
    void uploadFile_ShouldReturnCloudinaryUploadResponse_WhenUploadSuccess() throws IOException {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        byte[] fileBytes = "fake-image".getBytes();

        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(file.getBytes()).thenReturn(fileBytes);

        Map<String, Object> uploadResponse = new HashMap<>();
        uploadResponse.put("public_id", "uploaded_public_id");
        uploadResponse.put("url", "https://res.cloudinary.com/uploaded_public_id.png");

        when(uploader.upload(eq(fileBytes), any())).thenReturn(uploadResponse);

        CloudinaryUploadResponse expectedResponse = new CloudinaryUploadResponse(
                "uploaded_public_id", "uploaded_public_id", "https://res.cloudinary.com/uploaded_public_id.png");

        when(objectMapper.convertValue(uploadResponse, CloudinaryUploadResponse.class))
                .thenReturn(expectedResponse);

        // Act
        CloudinaryUploadResponse actualResponse = cloudinaryUploader.uploadFile(file, new HashMap<>());

        // Assert
        assertEquals("uploaded_public_id", actualResponse.publicId());
        assertEquals("https://res.cloudinary.com/uploaded_public_id.png", actualResponse.url());
        verify(uploader).upload(eq(fileBytes), any());
    }

    @Test
    void uploadFile_ShouldThrowRuntimeException_WhenIOExceptionOccurs() throws IOException {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("Simulated IO error"));

        // Act + Assert
        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> cloudinaryUploader.uploadFile(file, new HashMap<>()));

        assertTrue(exception.getMessage().contains("Uploading image to Cloudinary failed"));
    }

    @Test
    void deleteFile_ShouldSucceed_WhenDestroyReturnsOk() throws IOException {
        // Arrange
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);

        Map<String, Object> destroyResponse = new HashMap<>();
        destroyResponse.put("result", "ok");

        when(uploader.destroy(eq("test_public_id"), any())).thenReturn(destroyResponse);

        // Act
        cloudinaryUploader.deleteFile("test_public_id");

        // Assert
        verify(uploader, times(1)).destroy(eq("test_public_id"), any());
    }

    @Test
    void deleteFile_ShouldRetry_WhenDestroyThrowsIOException() throws IOException {
        // Arrange
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(anyString(), any())).thenThrow(new IOException("Simulated IO error"));

        // Act
        cloudinaryUploader.deleteFile("test_retry_public_id");

        // Assert
        verify(uploader, times(3)).destroy(eq("test_retry_public_id"), any());
    }

    @Test
    void getFileUrl_ShouldReturnGeneratedUrl() {
        // Arrange
        Url url = mock(Url.class);
        when(cloudinary.url()).thenReturn(url);
        when(url.publicId("sample_public_id")).thenReturn(url);
        when(url.secure(true)).thenReturn(url);
        when(url.generate()).thenReturn("https://res.cloudinary.com/sample_public_id.png");

        // Act
        String resultUrl = cloudinaryUploader.getFileUrl("sample_public_id");

        // Assert
        assertEquals("https://res.cloudinary.com/sample_public_id.png", resultUrl);
    }
}
