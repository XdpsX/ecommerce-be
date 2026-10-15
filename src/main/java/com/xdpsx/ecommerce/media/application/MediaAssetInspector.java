package com.xdpsx.ecommerce.media.application;

import java.time.LocalDateTime;
import java.util.Map;

/** Inspects an asset by its stored provider identity; caller-controlled identifiers are never accepted. */
public interface MediaAssetInspector {
    InspectedMediaAsset inspect(String storedPublicId);

    record InspectedMediaAsset(
            String publicId,
            String assetId,
            String resourceType,
            String deliveryType,
            String format,
            String secureUrl,
            long bytes,
            int width,
            int height,
            LocalDateTime createdAt,
            Map<String, String> context) {}
}
