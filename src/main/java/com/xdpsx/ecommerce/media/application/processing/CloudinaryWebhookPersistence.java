package com.xdpsx.ecommerce.media.application.processing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.config.MediaReconciliationProperties;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryEagerNotification;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryEagerOutcomeEvaluator;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CloudinaryWebhookPersistence {
    private final MediaRepository mediaRepository;
    private final Clock clock;
    private final MediaUploadProperties uploadProperties;
    private final MediaReconciliationProperties reconciliationProperties;

    public CloudinaryWebhookPersistence(
            MediaRepository mediaRepository,
            Clock clock,
            MediaUploadProperties uploadProperties,
            MediaReconciliationProperties reconciliationProperties) {
        this.mediaRepository = mediaRepository;
        this.clock = clock;
        this.uploadProperties = uploadProperties;
        this.reconciliationProperties = reconciliationProperties;
    }

    @Transactional
    public MergeResult merge(
            CloudinaryEagerNotification notification, CloudinaryEagerOutcomeEvaluator outcomeEvaluator) {
        Media media;
        if (present(notification.publicId())) {
            media = mediaRepository
                    .findByExternalIdForUpdate(notification.publicId())
                    .orElse(null);
            if (media == null) return new MergeResult(CloudinaryEagerWebhookHandler.Result.IGNORED, null, null);
        } else {
            media = mediaRepository
                    .findByProcessingReferenceForUpdate(notification.batchId())
                    .orElse(null);
            if (media == null) {
                return new MergeResult(
                        isFreshLegacyRace(notification.eventTime())
                                ? CloudinaryEagerWebhookHandler.Result.NOT_VISIBLE_YET
                                : CloudinaryEagerWebhookHandler.Result.IGNORED,
                        null,
                        null);
            }
            if (media.isSignedUpload()) {
                return new MergeResult(CloudinaryEagerWebhookHandler.Result.IGNORED, media.getId(), null);
            }
        }

        if (media.getAttachmentStatus() == MediaAttachmentStatus.PENDING_DELETE) {
            return new MergeResult(CloudinaryEagerWebhookHandler.Result.DUPLICATE_OR_CONFLICT, media.getId(), null);
        }
        if (media.isSignedUpload() && !present(notification.assetId())) {
            log.warn("Ignoring signed Cloudinary callback without immutable asset ID, mediaId={}", media.getId());
            return new MergeResult(CloudinaryEagerWebhookHandler.Result.DUPLICATE_OR_CONFLICT, media.getId(), null);
        }
        if (present(notification.assetId()) && !media.bindWebhookIdentity(notification.assetId())) {
            log.warn("Ignoring Cloudinary asset identity conflict, mediaId={}", media.getId());
            return new MergeResult(CloudinaryEagerWebhookHandler.Result.DUPLICATE_OR_CONFLICT, media.getId(), null);
        }

        CloudinaryEagerOutcomeEvaluator.Outcome outcome = outcomeEvaluator.evaluate(notification, media.getPurpose());
        boolean applied = false;
        if (outcome == CloudinaryEagerOutcomeEvaluator.Outcome.READY) {
            applied = media.applyWebhookProcessingOutcome(true, null);
        } else if (outcome == CloudinaryEagerOutcomeEvaluator.Outcome.FAILED) {
            applied = media.applyWebhookProcessingOutcome(false, notification.failureReason());
        }
        if (present(notification.assetId()) && media.isSignedUpload()) {
            String batch = present(notification.batchId()) ? notification.batchId() : null;
            return new MergeResult(
                    applied
                            ? CloudinaryEagerWebhookHandler.Result.APPLIED
                            : CloudinaryEagerWebhookHandler.Result.DUPLICATE_OR_CONFLICT,
                    media.getId(),
                    batch);
        }
        if (outcome == CloudinaryEagerOutcomeEvaluator.Outcome.INCONCLUSIVE && present(notification.assetId())) {
            return new MergeResult(CloudinaryEagerWebhookHandler.Result.APPLIED, media.getId(), null);
        }
        return new MergeResult(
                applied
                        ? CloudinaryEagerWebhookHandler.Result.APPLIED
                        : CloudinaryEagerWebhookHandler.Result.DUPLICATE_OR_CONFLICT,
                media.getId(),
                null);
    }

    private boolean isFreshLegacyRace(Instant eventTime) {
        if (eventTime == null) return false;
        Instant now = clock.instant();
        Duration skew = uploadProperties.getClockSkewTolerance();
        return !eventTime.isAfter(now.plus(skew))
                && !eventTime.isBefore(now.minus(reconciliationProperties.getLegacyLookupRaceWindow()));
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    public record MergeResult(
            CloudinaryEagerWebhookHandler.Result result, String mediaId, String batchReferenceToBind) {}
}
