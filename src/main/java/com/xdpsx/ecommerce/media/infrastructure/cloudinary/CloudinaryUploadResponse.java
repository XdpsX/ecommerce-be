package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

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
        @JsonProperty("asset_id") String assetId) {
    public CloudinaryUploadResponse(String displayName, String publicId, String url) {
        this(publicId, url, url, null, null, null, null, null, 0, 0, 0L, displayName, null, null);
    }
}
