package com.xdpsx.ecommerce.media.api.dto;

import java.util.Map;

import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Upload verification, provider processing, attachment lifecycle, and available URLs")
public record MediaDirectStatusResponse(
        String mediaId,
        MediaPurpose purpose,

        @Schema(description = "PENDING_UPLOAD until complete verifies the stored provider asset identity")
        MediaUploadStatus uploadStatus,

        @Schema(description = "Processing may continue independently after upload is verified")
        MediaProcessingStatus processingStatus,

        MediaAttachmentStatus attachmentStatus,

        @Schema(description = "Original URL; null until the upload has been verified", nullable = true)
        String originalUrl,

        @Schema(description = "Named variants; empty until uploadStatus is UPLOADED and processingStatus is READY")
        Map<String, String> variants) {}
