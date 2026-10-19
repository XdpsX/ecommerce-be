package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.exceptions.ApiException;
import com.cloudinary.utils.ObjectUtils;
import com.xdpsx.ecommerce.media.application.processing.InvalidMediaAssetResponseException;
import com.xdpsx.ecommerce.media.application.processing.MediaAssetInspector;
import com.xdpsx.ecommerce.media.application.processing.MediaAssetProviderException;

/** Reads the authoritative Cloudinary Admin API resource identified by the stored public ID. */
@Component
public class CloudinaryMediaAssetInspector implements MediaAssetInspector {
    private final Cloudinary cloudinary;

    public CloudinaryMediaAssetInspector(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public InspectedMediaAsset inspect(String storedPublicId) {
        Map<?, ?> resource;
        try {
            resource = cloudinary
                    .api()
                    .resource(
                            storedPublicId,
                            ObjectUtils.asMap("resource_type", "image", "type", "upload", "context", true));
        } catch (ApiException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().toLowerCase().contains("not found")) {
                return null;
            }
            throw new MediaAssetProviderException("Cloudinary Admin API request failed", exception);
        } catch (Exception exception) {
            throw new MediaAssetProviderException("Cloudinary Admin API request failed", exception);
        }
        if (resource == null) return null;
        try {
            Map<String, String> context = new LinkedHashMap<>();
            Object contextValue = resource.get("context");
            if (contextValue instanceof Map<?, ?> contextMap) {
                Object customValue = contextMap.get("custom");
                if (customValue instanceof Map<?, ?> custom) {
                    custom.forEach((key, value) -> {
                        if (key != null && value != null) context.put(key.toString(), value.toString());
                    });
                }
            }
            return new InspectedMediaAsset(
                    string(resource, "public_id"),
                    string(resource, "asset_id"),
                    string(resource, "resource_type"),
                    string(resource, "type"),
                    string(resource, "format"),
                    string(resource, "secure_url"),
                    number(resource, "bytes").longValue(),
                    number(resource, "width").intValue(),
                    number(resource, "height").intValue(),
                    LocalDateTime.ofInstant(Instant.parse(string(resource, "created_at")), ZoneOffset.UTC),
                    Map.copyOf(context));
        } catch (InvalidMediaAssetResponseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidMediaAssetResponseException("Cloudinary resource response is malformed", exception);
        }
    }

    @Override
    public ProcessingInspection inspectProcessing(
            String storedPublicId, int maxPages, Set<String> expectedTransformations) {
        if (maxPages < 1) throw new IllegalArgumentException("maxPages must be positive");
        Set<String> transformations = new HashSet<>();
        Map<?, ?> resource = null;
        String cursor = null;
        int pagesRead = 0;
        try {
            do {
                Map<String, Object> options = new LinkedHashMap<>();
                options.put("resource_type", "image");
                options.put("type", "upload");
                options.put("context", true);
                options.put("derived", true);
                options.put("max_results", 100);
                if (cursor != null) options.put("derived_next_cursor", cursor);
                pagesRead++;
                resource = cloudinary.api().resource(storedPublicId, options);
                if (resource == null) return null;
                Object derivedValue = resource.get("derived");
                if (derivedValue instanceof Iterable<?> derived) {
                    for (Object item : derived) {
                        if (!(item instanceof Map<?, ?> derivedResource)) continue;
                        Object transformation = derivedResource.get("transformation");
                        if (transformation instanceof String value && !value.isBlank()) {
                            transformations.add(value.trim());
                        }
                    }
                }
                Object nextCursor = resource.get("derived_next_cursor");
                cursor = nextCursor == null || nextCursor.toString().isBlank() ? null : nextCursor.toString();
                if (transformations.containsAll(expectedTransformations)) break;
            } while (cursor != null && pagesRead < maxPages);
        } catch (ApiException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().toLowerCase().contains("not found")) {
                return new ProcessingInspection(null, null, null, null, Set.of(), false, pagesRead, false);
            }
            throw new MediaAssetProviderException(
                    "Cloudinary processing inspection failed", exception, Math.max(1, pagesRead));
        } catch (Exception exception) {
            if (exception instanceof MediaAssetProviderException providerException) throw providerException;
            throw new MediaAssetProviderException(
                    "Cloudinary processing inspection failed", exception, Math.max(1, pagesRead));
        }
        return new ProcessingInspection(
                string(resource, "public_id"),
                string(resource, "asset_id"),
                string(resource, "resource_type"),
                string(resource, "type"),
                Set.copyOf(transformations),
                cursor == null,
                pagesRead,
                true);
    }

    private static String string(Map<?, ?> resource, String key) {
        Object value = resource.get(key);
        return value == null ? null : value.toString();
    }

    private static Number number(Map<?, ?> resource, String key) {
        Object value = resource.get(key);
        if (value instanceof Number number) return number;
        throw new InvalidMediaAssetResponseException("Cloudinary resource omitted required numeric metadata");
    }
}
