package com.xdpsx.ecommerce.media.api.dto;

import java.time.Instant;
import java.util.Map;

import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

public record MediaUploadIntentResponse(
        String mediaId,
        Instant intentExpiresAt,
        Instant uploadAuthorizationExpiresAt,
        UploadInstructions upload,
        Status status) {
    public record UploadInstructions(String provider, String method, String url, Map<String, String> fields) {}

    public record Status(
            MediaUploadStatus upload, MediaProcessingStatus processing, MediaAttachmentStatus attachment) {}
}
