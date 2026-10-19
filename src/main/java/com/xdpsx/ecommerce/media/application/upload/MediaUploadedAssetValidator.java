package com.xdpsx.ecommerce.media.application.upload;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.application.processing.MediaAssetInspector.InspectedMediaAsset;
import com.xdpsx.ecommerce.media.application.upload.MediaDirectUploadPersistence.Snapshot;

/** Shared trust boundary for user-triggered completion and internal upload recovery. */
@Component
public class MediaUploadedAssetValidator {
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpg", "jpeg", "png", "gif");

    private final MediaUploadProperties properties;

    public MediaUploadedAssetValidator(MediaUploadProperties properties) {
        this.properties = properties;
    }

    public String validate(Snapshot snapshot, InspectedMediaAsset asset) {
        String format = asset.format() == null ? "" : asset.format().toLowerCase(Locale.ROOT);
        if (!snapshot.publicId().equals(asset.publicId())
                || blank(asset.assetId())
                || (snapshot.assetId() != null && !snapshot.assetId().equals(asset.assetId()))
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
                || asset.context() == null
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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
