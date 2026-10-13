package com.xdpsx.ecommerce.media.application.storage;

import java.util.Map;

import com.xdpsx.ecommerce.media.domain.MediaPurpose;

/** Generates delivery URLs without exposing provider-specific transformation syntax. */
public interface MediaUrlGenerator {
    Map<String, String> generateVariants(String externalId, MediaPurpose purpose);
}
