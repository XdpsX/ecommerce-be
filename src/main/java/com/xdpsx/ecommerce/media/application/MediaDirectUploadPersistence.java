package com.xdpsx.ecommerce.media.application;

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
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.RequiredArgsConstructor;

/** Owns short database transactions for direct upload snapshots, state transitions and row-lock serialization. */
@Component
@RequiredArgsConstructor
public class MediaDirectUploadPersistence {
    private final MediaRepository repository;
    private final Clock clock;
    private final MediaUploadProperties properties;

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
            LocalDateTime authorizationExpiresAt) {
        public boolean isSignedUpload() {
            return authorizationIssuedAt != null;
        }

        static Snapshot from(Media media) {
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
                    media.getUploadAuthorizationExpiresAt());
        }
    }
}
