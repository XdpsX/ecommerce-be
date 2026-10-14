package com.xdpsx.ecommerce.catalog.brand.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public Brand view. Lifecycle, version and admin category associations are intentionally not exposed.
 */
public record StorefrontBrandResponse(
        Integer id,
        String name,

        @Schema(description = "Brand-logo image URL; the original URL is used until eager processing is READY.")
        String image) {}
