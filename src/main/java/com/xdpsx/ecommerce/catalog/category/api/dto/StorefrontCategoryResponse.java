package com.xdpsx.ecommerce.catalog.category.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Storefront view of one effectively active Category, used for the root list
 * and the slug detail.
 * Admin-only fields (stored status, effective flag, display order) are
 * deliberately absent.
 */
public record StorefrontCategoryResponse(
        Integer id,
        String name,
        String slug,

        @Schema(description = "Category-card image URL; the original URL is used until eager processing is READY.")
        String image) {}
