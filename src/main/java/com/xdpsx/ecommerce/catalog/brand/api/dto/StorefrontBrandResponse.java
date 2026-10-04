package com.xdpsx.ecommerce.catalog.brand.api.dto;

/**
 * Public Brand view. Lifecycle, version and admin category associations are intentionally not exposed.
 */
public record StorefrontBrandResponse(Integer id, String name, String image) {}
