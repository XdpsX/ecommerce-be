package com.xdpsx.ecommerce.media.application.upload;

import java.time.LocalDateTime;
import java.util.Map;

import com.xdpsx.ecommerce.media.domain.MediaPurpose;

/** Creates a short-lived, provider-signed authorization for one backend-issued media identity. */
public interface MediaUploadAuthorizer {
    UploadAuthorization authorize(UploadAuthorizationRequest request);

    record UploadAuthorizationRequest(String mediaId, String publicId, MediaPurpose purpose) {}

    record UploadAuthorization(
            String uploadUrl, Map<String, String> fields, LocalDateTime issuedAt, LocalDateTime expiresAt) {}
}
