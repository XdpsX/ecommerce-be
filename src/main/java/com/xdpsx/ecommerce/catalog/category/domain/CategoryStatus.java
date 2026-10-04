package com.xdpsx.ecommerce.catalog.category.domain;

/**
 * Admin-controlled lifecycle of a single Category node.
 *
 * <p>Status describes only the node itself and is never cascaded to descendants. Storefront visibility
 * additionally requires every ancestor to be {@link #ACTIVE}; that effective status is a derived concept and is
 * not part of this enum.
 */
public enum CategoryStatus {
    ACTIVE,
    INACTIVE
}
