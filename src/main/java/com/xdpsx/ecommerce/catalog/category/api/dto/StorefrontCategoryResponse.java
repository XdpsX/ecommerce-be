package com.xdpsx.ecommerce.catalog.category.api.dto;

/**
 * Storefront view of one effectively active Category, used for the root list
 * and the slug detail.
 * Admin-only fields (stored status, effective flag, display order) are
 * deliberately absent.
 */
public record StorefrontCategoryResponse(Integer id, String name, String slug, String image) {}
