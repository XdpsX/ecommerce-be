package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CloudinaryEagerNotification(
        @JsonProperty("notification_type") String notificationType,
        @JsonProperty("batch_id") String batchId,
        String status,
        String state,
        String error,
        String reason,
        List<EagerResult> eager) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EagerResult(
            String transformation,
            @JsonProperty("secure_url") String secureUrl,
            String status,
            String state,
            String error,
            String reason) {}

    public Outcome outcome() {
        if (!"eager".equalsIgnoreCase(notificationType) || isBlank(batchId)) {
            throw new IllegalArgumentException("Invalid Cloudinary eager notification identity");
        }
        Outcome topLevel = outcome(status, state);
        if (topLevel != null) {
            return topLevel;
        }
        if (eager == null || eager.isEmpty()) {
            throw new IllegalArgumentException("Cloudinary eager notification has no outcome");
        }
        boolean hasSuccess = false;
        boolean hasExplicitOutcome = false;
        for (EagerResult result : eager) {
            if (result == null) {
                throw new IllegalArgumentException("Cloudinary eager notification contains an invalid result");
            }
            Outcome resultOutcome = outcome(result.status(), result.state());
            if (resultOutcome == null && (!isBlank(result.status()) || !isBlank(result.state()))) {
                throw new IllegalArgumentException("Cloudinary eager notification has an unknown outcome");
            }
            hasExplicitOutcome |= resultOutcome != null;
            if (resultOutcome == Outcome.FAILED) {
                return Outcome.FAILED;
            }
            if (resultOutcome == Outcome.READY) {
                hasSuccess = true;
            }
        }
        if (hasSuccess || !hasExplicitOutcome) {
            return Outcome.READY;
        }
        throw new IllegalArgumentException("Cloudinary eager notification has an unknown outcome");
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
        return null;
    }

    private static Outcome outcome(String status, String state) {
        String value = !isBlank(status) ? status : state;
        if (value == null) return null;
        return switch (value.trim().toLowerCase()) {
            case "success", "succeeded", "ready", "complete", "completed" -> Outcome.READY;
            case "failed", "failure", "error" -> Outcome.FAILED;
            default -> null;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum Outcome {
        READY,
        FAILED
    }
}
