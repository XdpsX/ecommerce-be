package com.xdpsx.ecommerce.media.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MediaTest {

    @Test
    void activate_ShouldMoveTemporaryToActive() {
        // Arrange
        Media media = Media.builder()
                .id("mediaId")
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .build();

        // Act
        media.activate();

        // Assert
        assertEquals(MediaAttachmentStatus.ACTIVE, media.getAttachmentStatus());
    }

    @Test
    void activate_ShouldRejectAlreadyActiveMedia() {
        // Arrange
        Media media = Media.builder()
                .id("mediaId")
                .attachmentStatus(MediaAttachmentStatus.ACTIVE)
                .build();

        // Act + Assert
        assertThrows(IllegalStateException.class, media::activate);
        assertEquals(MediaAttachmentStatus.ACTIVE, media.getAttachmentStatus());
    }

    @Test
    void markPendingDeletion_ShouldBeIdempotentFromAnyLiveStatus() {
        // Arrange
        Media temporary = Media.builder()
                .id("temp")
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .build();
        Media active = Media.builder()
                .id("active")
                .attachmentStatus(MediaAttachmentStatus.ACTIVE)
                .build();

        // Act
        temporary.markPendingDeletion();
        active.markPendingDeletion();
        active.markPendingDeletion();

        // Assert
        assertEquals(MediaAttachmentStatus.PENDING_DELETE, temporary.getAttachmentStatus());
        assertEquals(MediaAttachmentStatus.PENDING_DELETE, active.getAttachmentStatus());
    }
}
