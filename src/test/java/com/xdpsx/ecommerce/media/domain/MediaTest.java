package com.xdpsx.ecommerce.media.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MediaTest {

    @Test
    void activate_ShouldMoveTemporaryToActive() {
        // Arrange
        Media media =
                Media.builder().id("mediaId").status(MediaStatus.TEMPORARY).build();

        // Act
        media.activate();

        // Assert
        assertEquals(MediaStatus.ACTIVE, media.getStatus());
    }

    @Test
    void activate_ShouldRejectAlreadyActiveMedia() {
        // Arrange
        Media media = Media.builder().id("mediaId").status(MediaStatus.ACTIVE).build();

        // Act + Assert
        assertThrows(IllegalStateException.class, media::activate);
        assertEquals(MediaStatus.ACTIVE, media.getStatus());
    }

    @Test
    void markPendingDeletion_ShouldBeIdempotentFromAnyLiveStatus() {
        // Arrange
        Media temporary =
                Media.builder().id("temp").status(MediaStatus.TEMPORARY).build();
        Media active = Media.builder().id("active").status(MediaStatus.ACTIVE).build();

        // Act
        temporary.markPendingDeletion();
        active.markPendingDeletion();
        active.markPendingDeletion();

        // Assert
        assertEquals(MediaStatus.PENDING_DELETE, temporary.getStatus());
        assertEquals(MediaStatus.PENDING_DELETE, active.getStatus());
    }
}
