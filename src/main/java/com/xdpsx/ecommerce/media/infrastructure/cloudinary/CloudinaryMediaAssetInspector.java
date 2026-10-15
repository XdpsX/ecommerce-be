package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.exceptions.ApiException;
import com.cloudinary.utils.ObjectUtils;
import com.xdpsx.ecommerce.media.application.InvalidMediaAssetResponseException;
import com.xdpsx.ecommerce.media.application.MediaAssetInspector;
import com.xdpsx.ecommerce.media.application.MediaAssetProviderException;

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
            resource = cloudinary.api().resource(storedPublicId, ObjectUtils.asMap("context", true));
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
