package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.cloudinary.Cloudinary;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.application.MediaUploadAuthorizer;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

/** Signs only server-selected Cloudinary upload fields before the corresponding intent is persisted. */
@Component
public class CloudinaryUploadAuthorizer implements MediaUploadAuthorizer {
    private final Cloudinary cloudinary;
    private final CloudinaryMediaProperties cloudinaryProperties;
    private final MediaUploadProperties uploadProperties;
    private final Clock clock;

    public CloudinaryUploadAuthorizer(
            Cloudinary cloudinary,
            CloudinaryMediaProperties cloudinaryProperties,
            MediaUploadProperties uploadProperties,
            Clock clock) {
        this.cloudinary = cloudinary;
        this.cloudinaryProperties = cloudinaryProperties;
        this.uploadProperties = uploadProperties;
        this.clock = clock;
    }

    @Override
    public UploadAuthorization authorize(UploadAuthorizationRequest request) {
        LocalDateTime issuedAt =
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).withNano(0);
        long timestamp = issuedAt.toEpochSecond(ZoneOffset.UTC);
        // Configuration validation pins this to Cloudinary's actual one-hour signature window.
        LocalDateTime expiresAt = issuedAt.plus(uploadProperties.getAuthorizationLifetime());
        Map<String, Object> signed = new LinkedHashMap<>();
        signed.put("timestamp", timestamp);
        signed.put("public_id", request.publicId());
        signed.put("overwrite", "false");
        signed.put("allowed_formats", "jpg,jpeg,png,gif");
        signed.put("eager", eagerFor(request.purpose()));
        signed.put("eager_async", "true");
        signed.put("eager_notification_url", cloudinaryProperties.getEagerNotificationUrl());
        signed.put(
                "context",
                "media_id=" + request.mediaId() + "|purpose="
                        + request.purpose().name());

        String signature =
                cloudinary.apiSignRequest(signed, cloudinary.config.apiSecret, cloudinary.config.signatureVersion);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("api_key", cloudinary.config.apiKey);
        signed.forEach((key, value) -> fields.put(key, String.valueOf(value)));
        fields.put("signature", signature);
        return new UploadAuthorization(
                "https://api.cloudinary.com/v1_1/" + cloudinary.config.cloudName + "/image/upload",
                Map.copyOf(fields),
                issuedAt,
                expiresAt);
    }

    private static String eagerFor(MediaPurpose purpose) {
        return CloudinaryMediaTransformations.eagerFor(purpose).stream()
                .map(transformation -> transformation.generate())
                .collect(Collectors.joining("|"));
    }
}
