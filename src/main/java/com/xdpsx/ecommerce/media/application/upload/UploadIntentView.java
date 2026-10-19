package com.xdpsx.ecommerce.media.application.upload;

import java.time.LocalDateTime;
import java.util.Map;

import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

/** Internal result containing the signed provider fields returned with a persisted upload intent. */
public record UploadIntentView(
        String mediaId,
        String publicId,
        LocalDateTime intentExpiresAt,
        MediaUploadStatus uploadStatus,
        MediaProcessingStatus processingStatus,
        MediaAttachmentStatus attachmentStatus,
        LocalDateTime uploadAuthorizationExpiresAt,
        String uploadUrl,
        Map<String, String> uploadFields) {}
