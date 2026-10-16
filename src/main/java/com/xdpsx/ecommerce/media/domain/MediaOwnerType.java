package com.xdpsx.ecommerce.media.domain;

/** Catalog aggregate types that may own an attached Media. */
public enum MediaOwnerType {
    CATEGORY,
    BRAND,
    PRODUCT;

    public boolean supports(MediaPurpose purpose) {
        return switch (this) {
            case CATEGORY -> purpose == MediaPurpose.CATEGORY_IMAGE;
            case BRAND -> purpose == MediaPurpose.BRAND_LOGO;
            case PRODUCT -> purpose == MediaPurpose.PRODUCT_IMAGE || purpose == MediaPurpose.PRODUCT_DESCRIPTION_IMAGE;
        };
    }
}
