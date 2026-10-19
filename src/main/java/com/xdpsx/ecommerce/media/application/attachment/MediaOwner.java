package com.xdpsx.ecommerce.media.application.attachment;

import com.xdpsx.ecommerce.media.domain.MediaOwnerType;

/** Trusted owner identity constructed by Catalog application services, never accepted from an API request. */
public record MediaOwner(MediaOwnerType type, String id) {
    public MediaOwner {
        if (type == null || id == null || id.isBlank() || id.length() > 64) {
            throw new IllegalArgumentException(
                    "Media owner type and a nonblank ID of at most 64 characters are required");
        }
    }
}
