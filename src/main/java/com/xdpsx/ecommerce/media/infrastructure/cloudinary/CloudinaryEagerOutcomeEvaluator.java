package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.cloudinary.Transformation;
import com.xdpsx.ecommerce.media.application.storage.MediaVariant;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

/** Evaluates only catalogued eager transformations and requires evidence for every expected variant. */
@Component
public class CloudinaryEagerOutcomeEvaluator {
    public Outcome evaluate(CloudinaryEagerNotification notification, MediaPurpose purpose) {
        String topLevel = status(notification.status(), notification.state());
        if (isFailure(topLevel)) return Outcome.FAILED;

        Set<String> expected = new HashSet<>();
        for (MediaVariant variant : MediaVariant.forPurpose(purpose)) {
            Transformation<?> transformation = CloudinaryMediaTransformations.forVariant(variant);
            expected.add(transformation.generate());
        }
        if (expected.isEmpty() || notification.eager() == null) return Outcome.INCONCLUSIVE;

        Set<String> succeeded = new HashSet<>();
        for (CloudinaryEagerNotification.EagerResult result : notification.eager()) {
            if (result == null || result.transformation() == null) continue;
            String transformation = result.transformation().trim();
            if (!expected.contains(transformation)) continue;
            String resultStatus = status(result.status(), result.state());
            if (isFailure(resultStatus)) return Outcome.FAILED;
            if (isSuccess(resultStatus) || (resultStatus == null && validGeneratedResult(result))) {
                succeeded.add(transformation);
            }
        }
        return succeeded.containsAll(expected) ? Outcome.READY : Outcome.INCONCLUSIVE;
    }

    private static boolean validGeneratedResult(CloudinaryEagerNotification.EagerResult result) {
        return isCloudinarySecureUrl(result.secureUrl())
                && result.format() != null
                && Set.of("jpg", "jpeg", "png", "gif").contains(result.format().toLowerCase(Locale.ROOT))
                && result.bytes() != null
                && result.bytes() > 0
                && result.width() != null
                && result.width() > 0
                && result.height() != null
                && result.height() > 0;
    }

    private static boolean isCloudinarySecureUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && "res.cloudinary.com".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String status(String status, String state) {
        String value = status == null || status.isBlank() ? state : status;
        return value == null ? null : value.trim().toLowerCase();
    }

    private static boolean isFailure(String value) {
        return value != null && Set.of("failed", "failure", "error").contains(value);
    }

    private static boolean isSuccess(String value) {
        return value != null
                && Set.of("success", "succeeded", "ready", "complete", "completed")
                        .contains(value);
    }

    public enum Outcome {
        READY,
        FAILED,
        INCONCLUSIVE
    }
}
