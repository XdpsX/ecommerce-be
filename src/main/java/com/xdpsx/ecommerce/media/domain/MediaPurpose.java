package com.xdpsx.ecommerce.media.domain;

import java.util.Arrays;

/**
 * Business purpose of a Media asset.
 *
 * <p>This type is intentionally provider-neutral: it only describes how the application uses the
 * asset (API value and minimum width) and must not contain storage-provider concerns such as
 * folders, transformations or raw upload options. Provider mapping lives in the storage adapter.
 */
public enum MediaPurpose {
    CATEGORY_IMAGE("category", 280),
    BRAND_LOGO("brand", 280),
    PRODUCT_IMAGE("product", 560),
    ;

    private final String resource;
    private final Integer minWidth;

    MediaPurpose(String resource, Integer minWidth) {
        this.resource = resource;
        this.minWidth = minWidth;
    }

    public String resource() {
        return this.resource;
    }

    public Integer minWidth() {
        return this.minWidth;
    }

    public static MediaPurpose fromResource(String resource) {
        return Arrays.stream(MediaPurpose.values())
                .filter(purpose -> purpose.resource.equalsIgnoreCase(resource))
                .findFirst()
                .orElse(null);
    }
}
