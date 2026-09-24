package com.xdpsx.ecommerce.media.infrastructure.scheduling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.xdpsx.ecommerce.media.application.storage.MediaStorage;
import com.xdpsx.ecommerce.media.application.storage.MediaStorageException;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

class MediaCleanUpSchedulerTest {

    private MediaStorage mediaStorage;
    private MediaRepository mediaRepository;
    private MediaCleanUpScheduler mediaCleanUpScheduler;

    @BeforeEach
    void setUp() {
        mediaStorage = mock(MediaStorage.class);
        mediaRepository = mock(MediaRepository.class);
        mediaCleanUpScheduler = new MediaCleanUpScheduler(mediaStorage, mediaRepository);
    }

    @Test
    void cleanUpDeletedMedia_ShouldDeleteRecordOnlyAfterStorageDeletion() {
        // Arrange
        Media media = Media.builder()
                .id("mediaId")
                .externalId("test_external_id")
                .status(MediaStatus.PENDING_DELETE)
                .build();
        when(mediaRepository.findAllByStatus(MediaStatus.PENDING_DELETE)).thenReturn(List.of(media));

        // Act
        mediaCleanUpScheduler.cleanUpDeletedMedia();

        // Assert
        InOrder inOrder = inOrder(mediaStorage, mediaRepository);
        inOrder.verify(mediaStorage).delete("test_external_id");
        inOrder.verify(mediaRepository).delete(media);
    }

    @Test
    void cleanUpDeletedMedia_ShouldKeepRecord_WhenStorageDeletionFails() {
        // Arrange
        Media media = Media.builder()
                .id("mediaId")
                .externalId("failing_external_id")
                .status(MediaStatus.PENDING_DELETE)
                .build();
        when(mediaRepository.findAllByStatus(MediaStatus.PENDING_DELETE)).thenReturn(List.of(media));
        doThrow(new MediaStorageException("provider unavailable"))
                .when(mediaStorage)
                .delete("failing_external_id");

        // Act
        mediaCleanUpScheduler.cleanUpDeletedMedia();

        // Assert
        verify(mediaRepository, never()).delete(any());
    }

    @Test
    void cleanUpDeletedMedia_ShouldIsolateFailuresWithinTheBatch() {
        // Arrange
        Media failing = Media.builder()
                .id("failing")
                .externalId("failing_external_id")
                .status(MediaStatus.PENDING_DELETE)
                .build();
        Media healthy = Media.builder()
                .id("healthy")
                .externalId("healthy_external_id")
                .status(MediaStatus.PENDING_DELETE)
                .build();
        when(mediaRepository.findAllByStatus(MediaStatus.PENDING_DELETE)).thenReturn(List.of(failing, healthy));
        doThrow(new MediaStorageException("provider unavailable"))
                .when(mediaStorage)
                .delete("failing_external_id");

        // Act
        mediaCleanUpScheduler.cleanUpDeletedMedia();

        // Assert
        verify(mediaRepository).delete(healthy);
        verify(mediaRepository, never()).delete(failing);
    }

    @Test
    void cleanUpExpiredMedia_ShouldPersistPendingDeletionBeforeDeleting() {
        // Arrange
        Media media = Media.builder()
                .id("expiredId")
                .externalId("expired_external_id")
                .status(MediaStatus.TEMPORARY)
                .build();
        when(mediaRepository.findExpiredTemporaryMedia(any(LocalDateTime.class)))
                .thenReturn(List.of(media));

        // Act
        mediaCleanUpScheduler.cleanUpExpiredMedia();

        // Assert
        assertEquals(MediaStatus.PENDING_DELETE, media.getStatus());
        InOrder inOrder = inOrder(mediaRepository, mediaStorage);
        inOrder.verify(mediaRepository).save(media);
        inOrder.verify(mediaStorage).delete("expired_external_id");
        inOrder.verify(mediaRepository).delete(media);
    }

    @Test
    void cleanUpExpiredMedia_ShouldKeepRetryableRecord_WhenStorageDeletionFails() {
        // Arrange
        Media media = Media.builder()
                .id("expiredId")
                .externalId("expired_external_id")
                .status(MediaStatus.TEMPORARY)
                .build();
        when(mediaRepository.findExpiredTemporaryMedia(any(LocalDateTime.class)))
                .thenReturn(List.of(media));
        doThrow(new MediaStorageException("provider unavailable"))
                .when(mediaStorage)
                .delete("expired_external_id");

        // Act
        mediaCleanUpScheduler.cleanUpExpiredMedia();

        // Assert
        // The row must survive in PENDING_DELETE so the next pending-deletion run can retry it.
        assertEquals(MediaStatus.PENDING_DELETE, media.getStatus());
        verify(mediaRepository).save(media);
        verify(mediaRepository, never()).delete(any());
    }

    @Test
    void cleanUpJobs_ShouldDoNothing_WhenNoMediaMatches() {
        // Arrange
        when(mediaRepository.findAllByStatus(MediaStatus.PENDING_DELETE)).thenReturn(Collections.emptyList());
        when(mediaRepository.findExpiredTemporaryMedia(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // Act
        mediaCleanUpScheduler.cleanUpDeletedMedia();
        mediaCleanUpScheduler.cleanUpExpiredMedia();

        // Assert
        verify(mediaStorage, never()).delete(any());
        verify(mediaRepository, never()).delete(any());
    }
}
