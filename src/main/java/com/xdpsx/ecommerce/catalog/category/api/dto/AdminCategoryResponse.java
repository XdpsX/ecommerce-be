package com.xdpsx.ecommerce.catalog.category.api.dto;

import java.time.LocalDateTime;

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
        CategoryDTO parent,
        String imageId,
        Long createdBy,
        Long updatedBy,
        String createdByName,
        String updatedByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** Keeps existing callers source compatible while actor names are resolved at the admin read boundary. */
    public AdminCategoryResponse(
            Integer id,
            String name,
            String slug,
            CategoryStatus status,
            Long version,
            boolean effectivelyActive,
            Integer displayOrder,
            String image,
            CategoryDTO parent,
            String imageId,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this(
                id,
                name,
                slug,
                status,
                version,
                effectivelyActive,
                displayOrder,
                image,
                parent,
                imageId,
                createdBy,
                updatedBy,
                null,
                null,
                createdAt,
                updatedAt);
    }

    public record CategoryDTO(Integer id, String name) {}
}
