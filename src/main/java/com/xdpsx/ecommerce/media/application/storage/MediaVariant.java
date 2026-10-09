package com.xdpsx.ecommerce.media.application.storage;

import java.util.Arrays;
import java.util.List;

import com.xdpsx.ecommerce.media.domain.MediaPurpose;

/** Provider-neutral names for the small set of variants exposed by the media API. */
public enum MediaVariant {
    PRODUCT_CARD("productCard", MediaPurpose.PRODUCT_IMAGE),
    PRODUCT_DETAIL("productDetail", MediaPurpose.PRODUCT_IMAGE),
    PRODUCT_CONTENT("productContent", MediaPurpose.PRODUCT_DESCRIPTION_IMAGE),
    CATEGORY_CARD("categoryCard", MediaPurpose.CATEGORY_IMAGE),
    BRAND_LOGO("brandLogo", MediaPurpose.BRAND_LOGO);

    private final String apiName;
    private final MediaPurpose purpose;

    MediaVariant(String apiName, MediaPurpose purpose) {
        this.apiName = apiName;
        this.purpose = purpose;
    }

    public String apiName() {
        return apiName;
    }

    public MediaPurpose purpose() {
        return purpose;
    }

    public static List<MediaVariant> forPurpose(MediaPurpose purpose) {
        return Arrays.stream(values())
                .filter(variant -> variant.purpose == purpose)
                .toList();
    }
}
