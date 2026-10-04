package com.xdpsx.ecommerce.catalog.category.api.dto;

import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;

/**
 * Admin view of a Category. Carries both the admin-assigned stored
 * {@code status} and the derived
 * {@code effectivelyActive} flag (node and every ancestor stored
 * {@code ACTIVE}), so an admin can see when a
 * stored-active node is hidden from the storefront by an inactive ancestor. The {@code version} is the token to send
 * back when updating or deleting this Category.
 */
public record AdminCategoryResponse(
        Integer id,
        String name,
        String slug,
        CategoryStatus status,
        Long version,
        boolean effectivelyActive,
        Integer displayOrder,
        String image,
        CategoryDTO parent) {
    public record CategoryDTO(Integer id, String name) {}
}
