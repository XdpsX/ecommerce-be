package com.xdpsx.ecommerce.media.application.processing;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/** Inspects an asset by its stored provider identity; caller-controlled identifiers are never accepted. */
public interface MediaAssetInspector {
    InspectedMediaAsset inspect(String storedPublicId);

    ProcessingInspection inspectProcessing(String storedPublicId, int maxPages, Set<String> expectedTransformations);

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

    record ProcessingInspection(
            String publicId,
            String assetId,
            String resourceType,
            String deliveryType,
            Set<String> generatedTransformations,
            boolean inventoryComplete,
            int pagesRead,
            boolean resourceFound) {
        public ProcessingInspection(
                String publicId,
                String assetId,
                String resourceType,
                String deliveryType,
                Set<String> generatedTransformations,
                boolean inventoryComplete,
                int pagesRead) {
            this(
                    publicId,
                    assetId,
                    resourceType,
                    deliveryType,
                    generatedTransformations,
                    inventoryComplete,
                    pagesRead,
                    true);
        }
    }
}
