package com.xdpsx.ecommerce.scheduled;

import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.entities.Media;
import com.xdpsx.ecommerce.repositories.MediaRepository;
import com.xdpsx.ecommerce.utils.CloudinaryUploader;

class MediaCleanUpSchedulerTest {

    private CloudinaryUploader cloudinaryUploader;
    private MediaRepository mediaRepository;
    private MediaCleanUpScheduler mediaCleanUpScheduler;

    @BeforeEach
    void setUp() {
        cloudinaryUploader = mock(CloudinaryUploader.class);
        mediaRepository = mock(MediaRepository.class);
        mediaCleanUpScheduler = new MediaCleanUpScheduler(cloudinaryUploader, mediaRepository);
    }

    @Test
    void cleanUpDeletedMedia_ShouldDeleteMedia_WhenMediaMarkedDeleted() {
        // Arrange
        Media media = new Media();
        media.setExternalId("test_external_id");

        when(mediaRepository.findDeletedMedia()).thenReturn(List.of(media));

        // Act
        mediaCleanUpScheduler.cleanUpDeletedMedia();

        // Assert
        verify(cloudinaryUploader).deleteFile("test_external_id");
        verify(mediaRepository).delete(media);
    }

    @Test
    void cleanUpExpiredMedia_ShouldDeleteMedia_WhenMediaExpired() {
        // Arrange
        Media media = new Media();
        media.setExternalId("expired_external_id");

        when(mediaRepository.findExpiredMedia(any(LocalDateTime.class))).thenReturn(List.of(media));

        // Act
        mediaCleanUpScheduler.cleanUpExpiredMedia();

        // Assert
        verify(cloudinaryUploader).deleteFile("expired_external_id");
        verify(mediaRepository).delete(media);
    }

    @Test
    void cleanUpDeletedMedia_ShouldDoNothing_WhenNoDeletedMedia() {
        // Arrange
        when(mediaRepository.findDeletedMedia()).thenReturn(Collections.emptyList());

        // Act
        mediaCleanUpScheduler.cleanUpDeletedMedia();

        // Assert
        verify(cloudinaryUploader, never()).deleteFile(any());
        verify(mediaRepository, never()).delete(any());
    }

    @Test
    void cleanUpExpiredMedia_ShouldDoNothing_WhenNoExpiredMedia() {
        // Arrange
        when(mediaRepository.findExpiredMedia(any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        // Act
        mediaCleanUpScheduler.cleanUpExpiredMedia();

        // Assert
        verify(cloudinaryUploader, never()).deleteFile(any());
        verify(mediaRepository, never()).delete(any());
    }
}
