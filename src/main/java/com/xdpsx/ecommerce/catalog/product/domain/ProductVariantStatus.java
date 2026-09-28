package com.xdpsx.ecommerce.catalog.product.domain;

/** Lifecycle of a Product Variant/SKU. Availability is owned by Inventory in a later CR. */
public enum ProductVariantStatus {
    ACTIVE,
    INACTIVE
}
