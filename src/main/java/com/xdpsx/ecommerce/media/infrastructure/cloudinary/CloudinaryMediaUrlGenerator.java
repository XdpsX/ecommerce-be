package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.media.application.storage.MediaUrlGenerator;
import com.xdpsx.ecommerce.media.application.storage.MediaVariant;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CloudinaryMediaUrlGenerator implements MediaUrlGenerator {
    private final CloudinaryUploader cloudinaryUploader;

    @Override
    public Map<String, String> generateVariants(String externalId, MediaPurpose purpose) {
        Map<String, String> variants = new LinkedHashMap<>();
        for (MediaVariant variant : MediaVariant.forPurpose(purpose)) {
            variants.put(
                    variant.apiName(),
                    cloudinaryUploader.getFileUrl(externalId, CloudinaryMediaTransformations.forVariant(variant)));
        }
        return variants;
    }
}
