package com.xdpsx.ecommerce.catalog.category.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;

/**
 * Admin create payload. Slug and display order are server-owned: the slug is generated from {@code name} and the
 * node is appended to the end of its sibling group.
 *
 * <p>{@code parentId} selects the sibling group the new node is appended to; {@code null} appends it to the root
 * group. Reordering an existing node is a separate move/reorder operation.
 */
public record CreateCategoryRequest(
        @NotBlank @Size(max = 128) String name, CategoryStatus status, String imageId, Integer parentId) {}
