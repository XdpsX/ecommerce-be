package com.xdpsx.ecommerce.catalog.category.api.dto;

import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;

/**
 * Admin view of a Category. Carries the admin-assigned stored {@code status}; a derived effective status is
 * intentionally not exposed because it requires the whole ancestor chain to be correct.
 */
public record AdminCategoryResponse(
        Integer id,
        String name,
        String slug,
        CategoryStatus status,
        Integer displayOrder,
        String image,
        CategoryDTO parent) {
    public record CategoryDTO(Integer id, String name) {}
}
