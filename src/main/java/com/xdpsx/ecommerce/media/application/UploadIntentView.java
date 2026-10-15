package com.xdpsx.ecommerce.media.application;

import java.time.LocalDateTime;

import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

/** Internal result for PR C; it intentionally contains no provider authorization or signature. */
public record UploadIntentView(
        String mediaId,
        String publicId,
        LocalDateTime intentExpiresAt,
        MediaUploadStatus uploadStatus,
        MediaProcessingStatus processingStatus,
        MediaAttachmentStatus attachmentStatus) {}
