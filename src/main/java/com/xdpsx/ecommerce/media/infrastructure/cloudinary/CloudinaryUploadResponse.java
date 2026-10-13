package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CloudinaryUploadResponse(
        @JsonProperty("public_id") String publicId,
        @JsonProperty("url") String url,
        @JsonProperty("secure_url") String secureUrl,
        String format,
        @JsonProperty("resource_type") String resourceType,
        @JsonProperty("created_at") String createdAt,
        String type,
        @JsonProperty("original_filename") String originalFilename,
        int width,
        int height,
        long bytes,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("asset_folder") String assetFolder,
        @JsonProperty("asset_id") String assetId,
        @JsonProperty("batch_id") String batchId,
        List<EagerResult> eager) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EagerResult(@JsonProperty("batch_id") String batchId) {}

    /** Returns the batch shared by all eager transformations, including the legacy top-level shape. */
    public String processingBatchId() {
        String reference = batchId;
        if (eager != null) {
            for (EagerResult result : eager) {
                if (result == null
                        || result.batchId() == null
                        || result.batchId().isBlank()) {
                    return null;
                }
                if (reference != null && !reference.equals(result.batchId())) {
                    return null;
                }
                reference = result.batchId();
            }
        }
        return reference;
    }

    public CloudinaryUploadResponse(
            String publicId,
            String url,
            String secureUrl,
            String format,
            String resourceType,
            String createdAt,
            String type,
            String originalFilename,
            int width,
            int height,
            long bytes,
            String displayName,
            String assetFolder,
            String assetId,
            String batchId) {
        this(
                publicId,
                url,
                secureUrl,
                format,
                resourceType,
                createdAt,
                type,
                originalFilename,
                width,
                height,
                bytes,
                displayName,
                assetFolder,
                assetId,
                batchId,
                null);
    }

    public CloudinaryUploadResponse(
            String publicId,
            String url,
            String secureUrl,
            String format,
            String resourceType,
            String createdAt,
            String type,
            String originalFilename,
            int width,
            int height,
            long bytes,
            String displayName,
            String assetFolder,
            String assetId) {
        this(
                publicId,
                url,
                secureUrl,
                format,
                resourceType,
                createdAt,
                type,
                originalFilename,
                width,
                height,
                bytes,
                displayName,
                assetFolder,
                assetId,
                null);
    }

    public CloudinaryUploadResponse(String displayName, String publicId, String url) {
        this(publicId, url, url, null, null, null, null, null, 0, 0, 0L, displayName, null, null);
    }
}
