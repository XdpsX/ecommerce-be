package com.xdpsx.ecommerce.media.application;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.media.application.storage.MediaUrlGenerator;
import com.xdpsx.ecommerce.media.application.storage.MediaVariant;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;

import lombok.RequiredArgsConstructor;

/** Resolves a media URL for a catalog use case without exposing provider-specific transformations. */
@Service
@RequiredArgsConstructor
public class MediaUrlResolver {
    private final MediaUrlGenerator mediaUrlGenerator;

    /**
     * Returns the original URL until eager processing completes. Once ready, returns the named URL generated
     * from the same preset used for eager processing.
     */
    public String resolve(Media media, MediaVariant variant) {
        if (media == null) {
            return null;
        }
        if (media.getPurpose() != variant.purpose()) {
            throw new IllegalArgumentException("Media variant does not match media purpose");
        }
        if (media.getProcessingStatus() != MediaProcessingStatus.READY) {
            return media.getUrl();
        }

        Map<String, String> variants = mediaUrlGenerator.generateVariants(media.getExternalId(), media.getPurpose());
        String url = variants.get(variant.apiName());
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Generated media variant URL is missing: " + variant.apiName());
        }
        return url;
    }
}
