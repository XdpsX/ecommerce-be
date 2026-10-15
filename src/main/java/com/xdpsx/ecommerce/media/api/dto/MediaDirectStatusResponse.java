package com.xdpsx.ecommerce.media.api.dto;

import java.util.Map;

import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

public record MediaDirectStatusResponse(
        String mediaId,
        MediaPurpose purpose,
        MediaUploadStatus uploadStatus,
        MediaProcessingStatus processingStatus,
        MediaAttachmentStatus attachmentStatus,
        String originalUrl,
        Map<String, String> variants) {}
