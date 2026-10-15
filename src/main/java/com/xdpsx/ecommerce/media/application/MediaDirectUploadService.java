package com.xdpsx.ecommerce.media.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.api.dto.MediaDirectStatusResponse;
import com.xdpsx.ecommerce.media.application.MediaAssetInspector.InspectedMediaAsset;
import com.xdpsx.ecommerce.media.application.MediaDirectUploadPersistence.Snapshot;
import com.xdpsx.ecommerce.media.application.storage.MediaUrlGenerator;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MediaDirectUploadService {
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpg", "jpeg", "png", "gif");

    private final MediaDirectUploadPersistence persistence;
    private final MediaAssetInspector inspector;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final MediaUploadProperties properties;
    private final MediaUrlGenerator urlGenerator;
    private final Clock clock;

    public MediaDirectStatusResponse getStatus(String mediaId) {
        return response(persistence.snapshot(mediaId, currentUserIdProvider.getRequiredUserId()));
    }

    public MediaDirectStatusResponse complete(String mediaId) {
        Long uploaderId = currentUserIdProvider.getRequiredUserId();
        Snapshot snapshot = persistence.snapshot(mediaId, uploaderId);
        if (!snapshot.isSignedUpload()) throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_CONFLICT);
        if (snapshot.attachmentStatus() != com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus.TEMPORARY) {
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_CONFLICT);
        }
        if (snapshot.uploadStatus() == MediaUploadStatus.UPLOADED) return response(snapshot);

        LocalDateTime now = now();
        if (now.isAfter(snapshot.authorizationExpiresAt().plus(properties.getCompletionGrace()))) {
            persistence.claimExpired(mediaId, uploaderId);
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_EXPIRED);
        }

        InspectedMediaAsset asset;
        try {
            asset = inspector.inspect(snapshot.publicId());
        } catch (MediaAssetProviderException exception) {
            throw new ApplicationException(ErrorCode.MEDIA_PROVIDER_UNAVAILABLE, exception);
        } catch (InvalidMediaAssetResponseException exception) {
            persistence.reject(snapshot, uploaderId, "Cloudinary asset response omitted required metadata");
            throw new ApplicationException(ErrorCode.MEDIA_ASSET_INVALID, exception);
        }
        if (asset == null) throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_CONFLICT);

        String contentType = validate(snapshot, asset);
        if (contentType == null) {
            persistence.reject(snapshot, uploaderId, "Cloudinary asset does not satisfy the signed upload policy");
            throw new ApplicationException(ErrorCode.MEDIA_ASSET_INVALID);
        }
        Snapshot finalized = persistence.finalizeUpload(snapshot, uploaderId, asset, contentType);
        if (finalized.attachmentStatus() == com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus.PENDING_DELETE) {
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_EXPIRED);
        }
        return response(finalized);
    }

    public void delete(String mediaId) {
        persistence.delete(mediaId, currentUserIdProvider.getRequiredUserId());
    }

    private String validate(Snapshot snapshot, InspectedMediaAsset asset) {
        String format = asset.format() == null ? "" : asset.format().toLowerCase(Locale.ROOT);
        if (!snapshot.publicId().equals(asset.publicId())
                || blank(asset.assetId())
                || !"image".equals(asset.resourceType())
                || !"upload".equals(asset.deliveryType())
                || blank(asset.secureUrl())
                || !ALLOWED_FORMATS.contains(format)
                || asset.bytes() <= 0
                || asset.bytes() > properties.getMaxFileSize().toBytes()
                || asset.width() <= 0
                || asset.height() <= 0
                || (snapshot.purpose().minWidth() != null
                        && asset.width() < snapshot.purpose().minWidth())
                || !snapshot.id().equals(asset.context().get("media_id"))
                || !snapshot.purpose().name().equals(asset.context().get("purpose"))
                || asset.createdAt() == null
                || asset.createdAt()
                        .isBefore(snapshot.authorizationIssuedAt().minus(properties.getClockSkewTolerance()))
                || asset.createdAt().isAfter(snapshot.authorizationExpiresAt())) {
            return null;
        }
        return switch (format) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> null;
        };
    }

    private MediaDirectStatusResponse response(Snapshot snapshot) {
        Map<String, String> variants = snapshot.processingStatus() == MediaProcessingStatus.READY
                        && snapshot.uploadStatus() == MediaUploadStatus.UPLOADED
                ? urlGenerator.generateVariants(snapshot.publicId(), snapshot.purpose())
                : Map.of();
        return new MediaDirectStatusResponse(
                snapshot.id(),
                snapshot.purpose(),
                snapshot.uploadStatus(),
                snapshot.processingStatus(),
                snapshot.attachmentStatus(),
                snapshot.url(),
                variants);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
