package com.xdpsx.ecommerce.media.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class MediaTest {

    @Test
    void activate_ShouldMoveTemporaryToActive() {
        // Arrange
        Media media = uploadedMedia("mediaId", MediaAttachmentStatus.TEMPORARY);

        // Act
        media.activate();

        // Assert
        assertEquals(MediaAttachmentStatus.ACTIVE, media.getAttachmentStatus());
    }

    @Test
    void activate_ShouldRejectAlreadyActiveMedia() {
        // Arrange
        Media media = uploadedMedia("mediaId", MediaAttachmentStatus.ACTIVE);

        // Act + Assert
        assertThrows(IllegalStateException.class, media::activate);
        assertEquals(MediaAttachmentStatus.ACTIVE, media.getAttachmentStatus());
    }

    @Test
    void markPendingDeletion_ShouldBeIdempotentForTemporaryMediaAndAllowAggregateDeletionOfActiveMedia() {
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

        // Assert
        assertEquals(MediaAttachmentStatus.PENDING_DELETE, temporary.getAttachmentStatus());
        assertEquals(MediaAttachmentStatus.PENDING_DELETE, active.getAttachmentStatus());
    }

    @Test
    void createPendingUpload_ShouldSetIntentStateWithoutPlaceholderAssetMetadata() {
        LocalDateTime expiry = LocalDateTime.of(2026, 1, 1, 1, 0);

        Media media = Media.createPendingUpload(
                "id", "product/id", 42L, MediaPurpose.PRODUCT_IMAGE, "image/png", 1024, expiry);

        assertEquals(MediaUploadStatus.PENDING_UPLOAD, media.getUploadStatus());
        assertEquals(MediaProcessingStatus.PENDING, media.getProcessingStatus());
        assertEquals(MediaAttachmentStatus.TEMPORARY, media.getAttachmentStatus());
        assertNull(media.getUrl());
        assertNull(media.getContentType());
        assertEquals("image/png", media.getExpectedContentType());
        assertEquals(1024L, media.getExpectedSizeBytes());
        assertEquals(expiry, media.getIntentExpiresAt());
    }

    @Test
    void validateState_ShouldRejectInvalidCrossLifecycleCombinations() {
        Media activePending =
                pendingMedia().attachmentStatus(MediaAttachmentStatus.ACTIVE).build();
        Media uploadedPendingProcessing = Media.builder()
                .id("uploaded-pending-processing")
                .externalId("public-id-2")
                .url("https://example.test/image.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .processingStatus(MediaProcessingStatus.PENDING)
                .build();

        assertThrows(IllegalStateException.class, activePending::validateState);
        assertThrows(IllegalStateException.class, uploadedPendingProcessing::validateState);
    }

    @Test
    void activate_ShouldRejectPendingUploadAndPendingDelete() {
        Media pending = Media.createPendingUpload(
                "pending", "product/pending", 42L, MediaPurpose.PRODUCT_IMAGE, "image/png", 10, LocalDateTime.now());
        Media deleted = Media.builder()
                .id("deleted")
                .externalId("deleted-public")
                .url("https://example.test/image.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .build();
        deleted.markPendingDeletion();

        assertThrows(IllegalStateException.class, pending::activate);
        assertThrows(IllegalStateException.class, deleted::activate);
    }

    @Test
    void activate_ShouldRejectUploadedStateWithoutVerifiedAssetMetadata() {
        Media invalid = Media.builder()
                .id("invalid-uploaded")
                .externalId("invalid-public")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .build();

        assertThrows(IllegalStateException.class, invalid::activate);
        assertEquals(MediaAttachmentStatus.TEMPORARY, invalid.getAttachmentStatus());
    }

    @Test
    void webhookIdentity_ShouldBeImmutableAndAllowTerminalProcessingBeforeUploadComplete() {
        Media pending = Media.createSignedPendingUpload(
                "pending-webhook",
                "product/pending-webhook",
                7L,
                MediaPurpose.PRODUCT_IMAGE,
                "image/png",
                1024,
                LocalDateTime.of(2026, 1, 2, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 1, 0));

        assertTrue(pending.bindWebhookIdentity("asset-1"));
        assertFalse(pending.bindWebhookIdentity("asset-2"));
        assertTrue(pending.applyWebhookProcessingOutcome(true, null));
        assertEquals(MediaProcessingStatus.READY, pending.getProcessingStatus());
        assertEquals(MediaUploadStatus.PENDING_UPLOAD, pending.getUploadStatus());

        pending.completeUpload(
                "asset-1",
                "https://cdn.example.test/image.png",
                "image/png",
                1024,
                800,
                600,
                "png",
                LocalDateTime.of(2026, 1, 1, 0, 1));
        assertEquals(MediaProcessingStatus.READY, pending.getProcessingStatus());
        assertEquals(MediaUploadStatus.UPLOADED, pending.getUploadStatus());
    }

    @Test
    void webhookAndRetryTransitions_ShouldNotResurrectDeleteOrOverflowSchedulingCounter() {
        Media media = Media.builder()
                .id("retry")
                .externalId("public-retry")
                .url("https://example.test/retry.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .uploadStatus(MediaUploadStatus.UPLOADED)
                .processingStatus(MediaProcessingStatus.PROCESSING)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .processingReconciliationAttempts(Integer.MAX_VALUE)
                .build();
        media.scheduleProcessingReconciliation(LocalDateTime.now().plusMinutes(5));
        assertEquals(Integer.MAX_VALUE, media.getProcessingReconciliationAttempts());

        media.markPendingDeletion();
        assertFalse(media.applyWebhookProcessingOutcome(true, null));
        assertEquals(MediaAttachmentStatus.PENDING_DELETE, media.getAttachmentStatus());
    }

    private static Media.MediaBuilder pendingMedia() {
        return Media.builder()
                .id("pending-active")
                .externalId("public-id-1")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .uploadStatus(MediaUploadStatus.PENDING_UPLOAD)
                .processingStatus(MediaProcessingStatus.PENDING)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .expectedContentType("image/png")
                .expectedSizeBytes(1024L)
                .intentExpiresAt(LocalDateTime.now());
    }

    private static Media uploadedMedia(String id, MediaAttachmentStatus status) {
        return Media.builder()
                .id(id)
                .externalId("public-" + id)
                .url("https://example.test/" + id + ".png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .attachmentStatus(status)
                .build();
    }
}
