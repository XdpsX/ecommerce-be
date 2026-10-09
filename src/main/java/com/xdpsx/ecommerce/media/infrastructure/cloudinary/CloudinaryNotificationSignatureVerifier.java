package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.cloudinary.Cloudinary;

@Component
class CloudinaryNotificationSignatureVerifier implements NotificationSignatureVerifier {
    private final Cloudinary cloudinary;
    private final Duration tolerance;
    private final Clock clock;

    @Autowired
    CloudinaryNotificationSignatureVerifier(
            Cloudinary cloudinary, @Value("${media.cloudinary.webhook-timestamp-tolerance:2h}") Duration tolerance) {
        this(cloudinary, tolerance, Clock.systemUTC());
    }

    CloudinaryNotificationSignatureVerifier(Cloudinary cloudinary, Duration tolerance, Clock clock) {
        this.cloudinary = cloudinary;
        this.tolerance = tolerance;
        this.clock = clock;
    }

    @Override
    public boolean isValid(String signature, String timestamp, String rawBody) {
        if (isBlank(signature) || isBlank(timestamp) || rawBody == null) return false;
        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        long age = Math.abs(clock.instant().getEpochSecond() - timestampSeconds);
        if (age > tolerance.toSeconds()) return false;
        return cloudinary.verifyNotificationSignature(rawBody, timestamp, signature, tolerance.toSeconds());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
