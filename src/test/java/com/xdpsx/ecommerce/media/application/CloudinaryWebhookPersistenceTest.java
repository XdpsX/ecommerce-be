package com.xdpsx.ecommerce.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.config.MediaReconciliationProperties;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryEagerNotification;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryEagerOutcomeEvaluator;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryMediaTransformations;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

class CloudinaryWebhookPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-15T02:30:00Z");

    private final MediaRepository repository = mock(MediaRepository.class);
    private final CloudinaryWebhookPersistence persistence = new CloudinaryWebhookPersistence(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new MediaUploadProperties(),
            new MediaReconciliationProperties());
    private final CloudinaryEagerOutcomeEvaluator evaluator = new CloudinaryEagerOutcomeEvaluator();

    @Test
    void merge_ShouldCorrelateByPublicIdAndApplyEarlyTerminalWithoutVerifyingUpload() {
        Media pending = Media.createSignedPendingUpload(
                "media-1",
                "product/media-1",
                42L,
                MediaPurpose.PRODUCT_IMAGE,
                "image/png",
                100,
                LocalDateTime.of(2026, 10, 15, 3, 30),
                LocalDateTime.of(2026, 10, 15, 2, 0),
                LocalDateTime.of(2026, 10, 15, 3, 0));
        when(repository.findByExternalIdForUpdate("product/media-1")).thenReturn(Optional.of(pending));
        var notification =
                notification("product/media-1", "asset-1", null, "success", successResults(MediaPurpose.PRODUCT_IMAGE));

        var result = persistence.merge(notification, evaluator);

        assertEquals(CloudinaryEagerWebhookHandler.Result.APPLIED, result.result());
        assertEquals("asset-1", pending.getAssetId());
        assertEquals(MediaProcessingStatus.READY, pending.getProcessingStatus());
        assertEquals(MediaUploadStatus.PENDING_UPLOAD, pending.getUploadStatus());
        assertEquals(MediaAttachmentStatus.TEMPORARY, pending.getAttachmentStatus());
        verify(repository).findByExternalIdForUpdate("product/media-1");
        verify(repository, never()).findByProcessingReferenceForUpdate("batch-1");
    }

    @Test
    void merge_ShouldIgnoreAssetMismatchAndNotFallBackWhenUnknownPublicIdWasProvided() {
        Media media = uploaded("asset-existing");
        when(repository.findByExternalIdForUpdate("product/media-1")).thenReturn(Optional.of(media));

        var mismatch =
                persistence.merge(notification("product/media-1", "asset-other", null, "failed", List.of()), evaluator);
        assertEquals(CloudinaryEagerWebhookHandler.Result.DUPLICATE_OR_CONFLICT, mismatch.result());
        assertEquals(MediaProcessingStatus.PROCESSING, media.getProcessingStatus());
        assertEquals("asset-existing", media.getAssetId());

        when(repository.findByExternalIdForUpdate("unknown/public-id")).thenReturn(Optional.empty());
        var unknown =
                persistence.merge(notification("unknown/public-id", null, "batch-1", "failed", List.of()), evaluator);
        assertEquals(CloudinaryEagerWebhookHandler.Result.IGNORED, unknown.result());
        verify(repository, never()).findByProcessingReferenceForUpdate(eq("batch-1"));
    }

    @Test
    void merge_ShouldReturn503OnlyForFreshLegacyBatchOnlyPersistenceRace() {
        when(repository.findByProcessingReferenceForUpdate("batch-race")).thenReturn(Optional.empty());
        var fresh = persistence.merge(notification(null, null, "batch-race", null, List.of()), evaluator);
        var stale = persistence.merge(
                new CloudinaryEagerNotification(
                        "eager",
                        "batch-race",
                        null,
                        null,
                        "2026-10-15T02:00:00Z",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()),
                evaluator);

        assertEquals(CloudinaryEagerWebhookHandler.Result.NOT_VISIBLE_YET, fresh.result());
        assertEquals(CloudinaryEagerWebhookHandler.Result.IGNORED, stale.result());
    }

    private static Media uploaded(String assetId) {
        return Media.builder()
                .id("legacy-media")
                .externalId("product/media-1")
                .assetId(assetId)
                .url("https://cdn.example.test/a.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .uploadStatus(MediaUploadStatus.UPLOADED)
                .processingStatus(MediaProcessingStatus.PROCESSING)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .build();
    }

    private static CloudinaryEagerNotification notification(
            String publicId,
            String assetId,
            String batchId,
            String status,
            List<CloudinaryEagerNotification.EagerResult> eager) {
        return new CloudinaryEagerNotification(
                "eager", batchId, publicId, assetId, NOW.toString(), status, null, null, null, null, eager);
    }

    private static List<CloudinaryEagerNotification.EagerResult> successResults(MediaPurpose purpose) {
        return CloudinaryMediaTransformations.eagerStringsFor(purpose).stream()
                .map(transformation -> new CloudinaryEagerNotification.EagerResult(
                        transformation,
                        "https://res.cloudinary.com/demo/image/upload/derived.png",
                        null,
                        null,
                        null,
                        null,
                        "png",
                        100L,
                        800,
                        600))
                .toList();
    }
}
