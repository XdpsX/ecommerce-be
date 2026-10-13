package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

class CloudinaryNotificationSignatureVerifierTest {
    private static final String API_SECRET = "test-api-secret";
    private static final String BODY = "{}";
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final String timestamp = String.valueOf(now.getEpochSecond());
    private final Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", "test-cloud",
            "api_key", "test-api-key",
            "api_secret", API_SECRET));
    private final CloudinaryNotificationSignatureVerifier verifier = new CloudinaryNotificationSignatureVerifier(
            cloudinary, Duration.ofHours(2), Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void isValid_ShouldVerifyRealSignatureAndRejectBodyOrSignatureChanges() {
        String signature = signature(BODY, timestamp);

        assertTrue(verifier.isValid(signature, timestamp, BODY));
        assertFalse(verifier.isValid(signature, timestamp, "{\"changed\":true}"));
        char replacement = signature.charAt(signature.length() - 1) == '0' ? '1' : '0';
        assertFalse(verifier.isValid(signature.substring(0, signature.length() - 1) + replacement, timestamp, BODY));
    }

    @Test
    void isValid_ShouldRejectMissingOrStaleHeaders() {
        String signature = signature(BODY, timestamp);

        assertFalse(verifier.isValid(null, timestamp, BODY));
        assertFalse(verifier.isValid(signature, String.valueOf(now.getEpochSecond() - 7201), BODY));
    }

    private static String signature(String body, String timestamp) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest((body + timestamp + API_SECRET).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
