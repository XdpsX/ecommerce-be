package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CloudinaryEagerNotification(
        @JsonProperty("notification_type") String notificationType,
        @JsonProperty("batch_id") String batchId,
        @JsonProperty("public_id") String publicId,
        @JsonProperty("asset_id") String assetId,
        String timestamp,
        String status,
        String state,
        String error,
        String reason,
        @JsonProperty("notification_context") NotificationContext notificationContext,
        List<EagerResult> eager) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotificationContext(String timestamp) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EagerResult(
            String transformation,
            @JsonProperty("secure_url") String secureUrl,
            String status,
            String state,
            String error,
            String reason,
            String format,
            Long bytes,
            Integer width,
            Integer height) {}

    public Instant eventTime() {
        String value = timestamp;
        if ((value == null || value.isBlank()) && notificationContext != null) {
            value = notificationContext.timestamp();
        }
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            try {
                long epoch = Long.parseLong(value);
                return Math.abs(epoch) >= 100_000_000_000L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
            } catch (RuntimeException invalidTimestamp) {
                return null;
            }
        }
    }

    public String failureReason() {
        if (!isBlank(reason)) return reason;
        if (!isBlank(error)) return error;
        if (eager != null) {
            for (EagerResult result : eager) {
                if (result != null) {
                    if (!isBlank(result.reason())) return result.reason();
                    if (!isBlank(result.error())) return result.error();
                }
            }
        }
        return "Cloudinary eager processing failed";
    }

    public boolean isValidEagerType() {
        return "eager".equalsIgnoreCase(notificationType);
    }

    public boolean hasIdentity() {
        return !isBlank(publicId) || !isBlank(batchId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
