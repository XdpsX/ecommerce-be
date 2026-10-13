package com.xdpsx.ecommerce.media.api.dto;

import java.util.Map;

public record UploadedMediaDTO(
        String id, String caption, String contentType, String url, Map<String, String> variants) {
    public UploadedMediaDTO {
        variants = variants == null ? Map.of() : Map.copyOf(variants);
    }
}
