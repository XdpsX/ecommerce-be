package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.exceptions.ApiException;
import com.cloudinary.utils.ObjectUtils;
import com.xdpsx.ecommerce.media.application.cleanup.MediaCleanupProvider;
import com.xdpsx.ecommerce.media.application.cleanup.MediaCleanupProviderException;

/** Performs exact Cloudinary Admin API inspection/deletion within the authorized Media identity scope. */
@Component
public class CloudinaryMediaCleanupProvider implements MediaCleanupProvider {
    private static final List<String> BACKEND_ISSUED_RESOURCE_TYPES = List.of("image", "video", "raw");
    private static final List<String> LEGACY_RESOURCE_TYPES = List.of("image");

    private final Cloudinary cloudinary;

    public CloudinaryMediaCleanupProvider(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public Inspection inspectExactPublicId(String publicId, CleanupScope scope) {
        List<AssetIdentity> assets = new ArrayList<>();
        List<String> resourceTypes =
                scope == CleanupScope.BACKEND_ISSUED ? BACKEND_ISSUED_RESOURCE_TYPES : LEGACY_RESOURCE_TYPES;
        for (String resourceType : resourceTypes) {
            Map<?, ?> response;
            try {
                response = cloudinary
                        .api()
                        .resource(publicId, ObjectUtils.asMap("resource_type", resourceType, "type", "upload"));
            } catch (ApiException exception) {
                if (isNotFound(exception)) continue;
                throw providerFailure("Cloudinary exact resource inspection failed", exception);
            } catch (Exception exception) {
                throw providerFailure("Cloudinary exact resource inspection failed", exception);
            }
            if (response == null) throw new MediaCleanupProviderException("Cloudinary returned no inspection result");
            String returnedPublicId = string(response, "public_id");
            String returnedResourceType = string(response, "resource_type");
            String deliveryType = string(response, "type");
            String assetId = string(response, "asset_id");
            if (!publicId.equals(returnedPublicId)
                    || !resourceType.equals(returnedResourceType)
                    || !"upload".equals(deliveryType)
                    || assetId == null
                    || assetId.isBlank()) {
                throw new MediaCleanupProviderException(
                        "Cloudinary returned incomplete or non-exact resource identity");
            }
            assets.add(new AssetIdentity(publicId, assetId, resourceType, deliveryType));
        }
        return new Inspection(assets);
    }

    @Override
    public void deleteExactAssets(List<AssetIdentity> assets) {
        if (assets.isEmpty()) return;
        List<String> assetIds =
                assets.stream().map(AssetIdentity::assetId).distinct().toList();
        try {
            Map<?, ?> response = cloudinary.api().deleteResourcesByAssetIds(assetIds, ObjectUtils.emptyMap());
            if (response == null || !(response.get("deleted") instanceof Map<?, ?> deleted)) {
                throw new MediaCleanupProviderException("Cloudinary deletion response omitted exact asset results");
            }
            Object notFoundValue = response.get("not_found");
            Map<?, ?> notFound = notFoundValue instanceof Map<?, ?> map ? map : Map.of();
            for (String assetId : assetIds) {
                Object result = deleted.get(assetId);
                if (result == null) result = notFound.get(assetId);
                if (!("deleted".equals(result) || "not found".equals(result))) {
                    throw new MediaCleanupProviderException("Cloudinary did not confirm exact asset deletion");
                }
            }
        } catch (MediaCleanupProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw providerFailure("Cloudinary exact asset deletion failed", exception);
        }
    }

    private static boolean isNotFound(ApiException exception) {
        String message = exception.getMessage();
        if (message == null) return false;
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("not found") || normalized.contains("404");
    }

    private static String string(Map<?, ?> response, String key) {
        Object value = response.get(key);
        return value == null ? null : value.toString();
    }

    private static MediaCleanupProviderException providerFailure(String message, Exception exception) {
        String details = message + " " + exception.getMessage();
        return new MediaCleanupProviderException(
                details.contains("429")
                                || details.toLowerCase(java.util.Locale.ROOT).contains("rate limit")
                        ? "Cloudinary rate limit 429"
                        : message,
                exception);
    }
}
