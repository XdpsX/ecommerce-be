package com.xdpsx.ecommerce.media.api.dto;

import java.time.Instant;
import java.util.Map;

import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Persisted upload intent and provider-issued upload instructions")
public record MediaUploadIntentResponse(
        String mediaId,

        @Schema(description = "Application intent expiration in UTC", example = "2026-10-16T12:00:00Z")
        Instant intentExpiresAt,

        @Schema(description = "Cloudinary upload signature expiration in UTC", example = "2026-10-16T12:00:00Z")
        Instant uploadAuthorizationExpiresAt,

        UploadInstructions upload,
        Status status) {
    @Schema(description = "Send these exact fields and the file to url using method; do not alter signed fields.")
    public record UploadInstructions(String provider, String method, String url, Map<String, String> fields) {}

    @Schema(description = "Media lifecycle immediately after intent creation")
    public record Status(
            MediaUploadStatus upload, MediaProcessingStatus processing, MediaAttachmentStatus attachment) {}
}
