package com.xdpsx.ecommerce.media.application.upload;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.application.processing.MediaAssetInspector;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

/** Owns short database transactions for direct upload snapshots, state transitions and row-lock serialization. */
@Component
public class MediaDirectUploadPersistence {
    private final MediaRepository repository;
    private final Clock clock;
    private final MediaUploadProperties properties;

    public MediaDirectUploadPersistence(MediaRepository repository, Clock clock, MediaUploadProperties properties) {
        this.repository = repository;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(String id, Long uploaderId) {
        return repository.findOwnedById(id, uploaderId).map(Snapshot::from).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public Snapshot delete(String id, Long uploaderId) {
        Media media = repository.findOwnedByIdForUpdate(id, uploaderId).orElseThrow(() -> notFound(id));
        if (media.getAttachmentStatus() == MediaAttachmentStatus.PENDING_DELETE) return Snapshot.from(media);
        if (media.getAttachmentStatus() != MediaAttachmentStatus.TEMPORARY
                || (media.getUploadStatus() != MediaUploadStatus.UPLOADED && !media.isSignedUpload())) {
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_CONFLICT);
        }
        media.markPendingDeletion();
        return Snapshot.from(repository.save(media));
    }

    @Transactional
    public Snapshot claimExpired(String id, Long uploaderId) {
        Media media = repository.findOwnedByIdForUpdate(id, uploaderId).orElseThrow(() -> notFound(id));
        if (media.getAttachmentStatus() == MediaAttachmentStatus.PENDING_DELETE) return Snapshot.from(media);
        if (media.getAttachmentStatus() != MediaAttachmentStatus.TEMPORARY
                || media.getUploadStatus() != MediaUploadStatus.PENDING_UPLOAD
                || !media.isSignedUpload()) {
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_CONFLICT);
        }
        media.markPendingDeletion();
        return Snapshot.from(repository.save(media));
    }

    @Transactional
    public Snapshot finalizeUpload(
            Snapshot expected,
            Long uploaderId,
            MediaAssetInspector.InspectedMediaAsset asset,
            String normalizedContentType) {
        Media media =
                repository.findOwnedByIdForUpdate(expected.id(), uploaderId).orElseThrow(() -> notFound(expected.id()));
        if (media.getAttachmentStatus() != MediaAttachmentStatus.TEMPORARY) {
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_CONFLICT);
        }
        if (media.getUploadStatus() == MediaUploadStatus.UPLOADED) {
            if (!Objects.equals(asset.assetId(), media.getAssetId())) {
                throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_CONFLICT);
            }
            return Snapshot.from(media);
        }
        if (!media.isSignedUpload()
                || !media.getExternalId().equals(expected.publicId())
                || !media.getUploadAuthorizationIssuedAt().equals(expected.authorizationIssuedAt())
                || !media.getUploadAuthorizationExpiresAt().equals(expected.authorizationExpiresAt())) {
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_CONFLICT);
        }
        if (media.getAssetId() != null && !Objects.equals(media.getAssetId(), asset.assetId())) {
            media.rejectSignedUpload("Cloudinary asset identity conflicts with the identity already bound to Media");
            return Snapshot.from(repository.save(media));
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (now.isAfter(media.getUploadAuthorizationExpiresAt().plus(properties.getCompletionGrace()))) {
            media.markPendingDeletion();
            return Snapshot.from(repository.save(media));
        }
        media.completeUpload(
                asset.assetId(),
                asset.secureUrl(),
                normalizedContentType,
                asset.bytes(),
                asset.width(),
                asset.height(),
                asset.format().toLowerCase(Locale.ROOT),
                asset.createdAt());
        return Snapshot.from(repository.save(media));
    }

    /** Internal recovery finalization intentionally does not inherit the HTTP completion grace deadline. */
    @Transactional
    public Snapshot recoverFinalize(
            Snapshot expected, MediaAssetInspector.InspectedMediaAsset asset, String normalizedContentType) {
        Media media = repository.findByIdForUpdate(expected.id()).orElseThrow(() -> notFound(expected.id()));
        if (media.getAttachmentStatus() != MediaAttachmentStatus.TEMPORARY
                || !media.isSignedUpload()
                || !Objects.equals(media.getExternalId(), expected.publicId())
                || !Objects.equals(media.getUploadAuthorizationIssuedAt(), expected.authorizationIssuedAt())
                || !Objects.equals(media.getUploadAuthorizationExpiresAt(), expected.authorizationExpiresAt())) {
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_CONFLICT);
        }
        if (media.getUploadStatus() == MediaUploadStatus.UPLOADED) {
            // A client complete may have finalized while recovery was outside the transaction. Never mutate
            // verified state from this stale provider read, even when the read reports a different identity.
            return Snapshot.from(media);
        }
        if (media.getAssetId() != null && !Objects.equals(media.getAssetId(), asset.assetId())) {
            media.rejectSignedUpload("Provider asset identity conflicts with the bound Media identity");
            return Snapshot.from(repository.save(media));
        }
        media.completeUpload(
                asset.assetId(),
                asset.secureUrl(),
                normalizedContentType,
                asset.bytes(),
                asset.width(),
                asset.height(),
                asset.format().toLowerCase(Locale.ROOT),
                asset.createdAt());
        return Snapshot.from(repository.save(media));
    }

    @Transactional
    public Snapshot rejectRecovery(Snapshot expected, String reason) {
        Media media = repository.findByIdForUpdate(expected.id()).orElseThrow(() -> notFound(expected.id()));
        if (media.getAttachmentStatus() == MediaAttachmentStatus.PENDING_DELETE) return Snapshot.from(media);
        if (media.getUploadStatus() != MediaUploadStatus.PENDING_UPLOAD
                || !media.isSignedUpload()
                || !Objects.equals(media.getExternalId(), expected.publicId())) {
            return Snapshot.from(media);
        }
        media.rejectSignedUpload(reason);
        return Snapshot.from(repository.save(media));
    }

    @Transactional
    public Snapshot claimAbsentRecovery(Snapshot expected, LocalDateTime now, LocalDateTime retryAt) {
        Media media = repository.findByIdForUpdate(expected.id()).orElseThrow(() -> notFound(expected.id()));
        if (media.getAttachmentStatus() != MediaAttachmentStatus.TEMPORARY
                || media.getUploadStatus() != MediaUploadStatus.PENDING_UPLOAD
                || !media.isSignedUpload()
                || !Objects.equals(media.getExternalId(), expected.publicId())
                || !Objects.equals(media.getAssetId(), expected.assetId())
                || media.getProcessingStatus() != expected.processingStatus()
                || !Objects.equals(media.getReconciliationNextAttemptAt(), expected.nextAttemptAt())) {
            return Snapshot.from(media);
        }
        LocalDateTime replayBoundary = media.getUploadAuthorizationExpiresAt().plus(properties.getReplayGrace());
        LocalDateTime clientGraceBoundary =
                media.getUploadAuthorizationExpiresAt().plus(properties.getCompletionGrace());
        LocalDateTime safeBoundary = replayBoundary.isAfter(clientGraceBoundary) ? replayBoundary : clientGraceBoundary;
        if (!now.isBefore(safeBoundary)) {
            media.markPendingDeletion();
        } else {
            media.scheduleUploadReconciliation(safeBoundary.isAfter(retryAt) ? safeBoundary : retryAt);
        }
        return Snapshot.from(repository.save(media));
    }

    @Transactional
    public void scheduleUploadRetry(Snapshot expected, LocalDateTime retryAt) {
        Media media = repository.findByIdForUpdate(expected.id()).orElse(null);
        if (media == null
                || media.getUploadStatus() != MediaUploadStatus.PENDING_UPLOAD
                || media.getAttachmentStatus() != MediaAttachmentStatus.TEMPORARY
                || !media.isSignedUpload()
                || !Objects.equals(media.getExternalId(), expected.publicId())
                || !Objects.equals(media.getAssetId(), expected.assetId())) return;
        media.scheduleUploadReconciliation(retryAt);
        repository.save(media);
    }

    @Transactional
    public void scheduleProcessingRetry(Snapshot expected, LocalDateTime retryAt) {
        Media media = repository.findByIdForUpdate(expected.id()).orElse(null);
        if (media == null
                || media.getUploadStatus() != MediaUploadStatus.UPLOADED
                || media.getProcessingStatus() != com.xdpsx.ecommerce.media.domain.MediaProcessingStatus.PROCESSING
                || media.getAttachmentStatus() == MediaAttachmentStatus.PENDING_DELETE
                || !Objects.equals(media.getExternalId(), expected.publicId())
                || !Objects.equals(media.getAssetId(), expected.assetId())) return;
        media.scheduleProcessingReconciliation(retryAt);
        repository.save(media);
    }

    @Transactional
    public boolean reconcileProcessingReady(Snapshot expected, String inspectedAssetId) {
        Media media = repository.findByIdForUpdate(expected.id()).orElse(null);
        if (media == null
                || media.getUploadStatus() != MediaUploadStatus.UPLOADED
                || media.getProcessingStatus() != com.xdpsx.ecommerce.media.domain.MediaProcessingStatus.PROCESSING
                || media.getAttachmentStatus() == MediaAttachmentStatus.PENDING_DELETE
                || !Objects.equals(media.getExternalId(), expected.publicId())
                || (media.getAssetId() != null && !Objects.equals(media.getAssetId(), inspectedAssetId))) return false;
        if (!media.applyVerifiedReconciliationReady(inspectedAssetId)) return false;
        repository.save(media);
        return true;
    }

    @Transactional
    public void reject(Snapshot expected, Long uploaderId, String reason) {
        Media media =
                repository.findOwnedByIdForUpdate(expected.id(), uploaderId).orElseThrow(() -> notFound(expected.id()));
        if (media.getAttachmentStatus() == MediaAttachmentStatus.PENDING_DELETE) return;
        if (!media.isSignedUpload() || media.getUploadStatus() != MediaUploadStatus.PENDING_UPLOAD) {
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_CONFLICT);
        }
        media.rejectSignedUpload(reason);
        repository.save(media);
    }

    public Snapshot snapshotForRecovery(String id) {
        return repository.findById(id).map(Snapshot::from).orElse(null);
    }

    private static ApplicationException notFound(String id) {
        return new ApplicationException(
                ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "media", "resourceId", id));
    }

    public record Snapshot(
            String id,
            String publicId,
            Long uploaderId,
            com.xdpsx.ecommerce.media.domain.MediaPurpose purpose,
            MediaUploadStatus uploadStatus,
            com.xdpsx.ecommerce.media.domain.MediaProcessingStatus processingStatus,
            MediaAttachmentStatus attachmentStatus,
            String url,
            String assetId,
            LocalDateTime intentExpiresAt,
            LocalDateTime authorizationIssuedAt,
            LocalDateTime authorizationExpiresAt,
            LocalDateTime uploadedAt,
            LocalDateTime createdAt,
            LocalDateTime nextAttemptAt,
            int processingAttempts,
            String uploadFailureReason) {
        public Snapshot(
                String id,
                String publicId,
                Long uploaderId,
                com.xdpsx.ecommerce.media.domain.MediaPurpose purpose,
                MediaUploadStatus uploadStatus,
                com.xdpsx.ecommerce.media.domain.MediaProcessingStatus processingStatus,
                MediaAttachmentStatus attachmentStatus,
                String url,
                String assetId,
                LocalDateTime intentExpiresAt,
                LocalDateTime authorizationIssuedAt,
                LocalDateTime authorizationExpiresAt) {
            this(
                    id,
                    publicId,
                    uploaderId,
                    purpose,
                    uploadStatus,
                    processingStatus,
                    attachmentStatus,
                    url,
                    assetId,
                    intentExpiresAt,
                    authorizationIssuedAt,
                    authorizationExpiresAt,
                    null,
                    null,
                    null,
                    0,
                    null);
        }

        public boolean isSignedUpload() {
            return authorizationIssuedAt != null;
        }

        public static Snapshot from(Media media) {
            return new Snapshot(
                    media.getId(),
                    media.getExternalId(),
                    media.getUploadedByUserId(),
                    media.getPurpose(),
                    media.getUploadStatus(),
                    media.getProcessingStatus(),
                    media.getAttachmentStatus(),
                    media.getUrl(),
                    media.getAssetId(),
                    media.getIntentExpiresAt(),
                    media.getUploadAuthorizationIssuedAt(),
                    media.getUploadAuthorizationExpiresAt(),
                    media.getUploadedAt(),
                    media.getCreatedAt(),
                    media.getReconciliationNextAttemptAt(),
                    media.getProcessingReconciliationAttempts(),
                    media.getUploadFailureReason());
        }
    }
}
