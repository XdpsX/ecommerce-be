package com.xdpsx.ecommerce.catalog.category.api.dto;

/**
 * Minimal Category projection for storefront responses that only need to identify the category a resource
 * belongs to. Admin-only fields are deliberately absent.
 */
public record CategorySummaryResponse(Integer id, String name, String slug) {}
