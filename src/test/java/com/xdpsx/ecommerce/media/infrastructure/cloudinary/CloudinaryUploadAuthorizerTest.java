package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cloudinary.Cloudinary;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.application.MediaUploadAuthorizer.UploadAuthorization;
import com.xdpsx.ecommerce.media.application.MediaUploadAuthorizer.UploadAuthorizationRequest;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

class CloudinaryUploadAuthorizerTest {
    private static final String SECRET = "test-api-secret-never-returned";

    @Test
    void authorize_ShouldReturnBackendPolicyWithSdkVerifiableSignatureAndNoSecret() {
        Cloudinary cloudinary = cloudinary();
        CloudinaryMediaProperties cloudinaryProperties = new CloudinaryMediaProperties();
        cloudinaryProperties.setEagerNotificationUrl("https://api.example.test/webhooks/cloudinary/eager");
        MediaUploadProperties properties = new MediaUploadProperties();
        properties.setAuthorizationLifetime(java.time.Duration.ofHours(1));
        CloudinaryUploadAuthorizer authorizer = new CloudinaryUploadAuthorizer(
                cloudinary,
                cloudinaryProperties,
                properties,
                Clock.fixed(Instant.parse("2026-10-15T02:00:00Z"), ZoneOffset.UTC));

        UploadAuthorization result = authorizer.authorize(
                new UploadAuthorizationRequest("media-123", "product/media-123", MediaPurpose.PRODUCT_IMAGE));

        Map<String, Object> signedParameters = new LinkedHashMap<>();
        result.fields().forEach((key, value) -> {
            if (!key.equals("api_key") && !key.equals("signature")) signedParameters.put(key, value);
        });
        assertEquals(
                cloudinary.apiSignRequest(signedParameters, SECRET, cloudinary.config.signatureVersion),
                result.fields().get("signature"));
        assertEquals("public-api-key", result.fields().get("api_key"));
        assertFalse(result.fields().containsValue(SECRET));
        assertEquals("false", result.fields().get("overwrite"));
        assertEquals("jpg,jpeg,png,gif", result.fields().get("allowed_formats"));
        assertEquals("true", result.fields().get("eager_async"));
        assertEquals("media_id=media-123|purpose=PRODUCT_IMAGE", result.fields().get("context"));
        assertEquals(
                "https://api.example.test/webhooks/cloudinary/eager",
                result.fields().get("eager_notification_url"));
        assertEquals("https://api.cloudinary.com/v1_1/test-cloud/image/upload", result.uploadUrl());
        assertTrue(result.fields().get("eager").contains("w_600"));
        assertEquals(Instant.parse("2026-10-15T02:00:00Z"), result.issuedAt().toInstant(ZoneOffset.UTC));
        assertEquals(result.issuedAt().plusHours(1), result.expiresAt());
    }

    @Test
    void authorize_ShouldSignDifferentBackendIssuedIdentityDifferently() {
        Cloudinary cloudinary = cloudinary();
        CloudinaryMediaProperties cloudinaryProperties = new CloudinaryMediaProperties();
        cloudinaryProperties.setEagerNotificationUrl("https://api.example.test/webhooks/cloudinary/eager");
        CloudinaryUploadAuthorizer authorizer = new CloudinaryUploadAuthorizer(
                cloudinary,
                cloudinaryProperties,
                new MediaUploadProperties(),
                Clock.fixed(Instant.parse("2026-10-15T02:00:00Z"), ZoneOffset.UTC));

        String first = authorizer
                .authorize(new UploadAuthorizationRequest("one", "product/one", MediaPurpose.PRODUCT_IMAGE))
                .fields()
                .get("signature");
        String second = authorizer
                .authorize(new UploadAuthorizationRequest("two", "product/two", MediaPurpose.PRODUCT_IMAGE))
                .fields()
                .get("signature");

        assertNotEquals(first, second);
    }

    private static Cloudinary cloudinary() {
        return new Cloudinary(Map.of("cloud_name", "test-cloud", "api_key", "public-api-key", "api_secret", SECRET));
    }
}
