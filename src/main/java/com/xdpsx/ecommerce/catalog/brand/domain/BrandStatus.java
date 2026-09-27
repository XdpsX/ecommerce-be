package com.xdpsx.ecommerce.catalog.brand.domain;

/**
 * Admin-controlled lifecycle of a Brand.
 *
 * <p>{@link #ACTIVE} means the Brand may appear in the storefront and can be selected for new write use cases.
 * {@link #INACTIVE} hides the Brand from the storefront and new selections, but never removes existing
 * Category associations or Products; how the Product storefront treats an inactive Brand is decided in the
 * Product renewal.
 */
public enum BrandStatus {
    ACTIVE,
    INACTIVE
}
