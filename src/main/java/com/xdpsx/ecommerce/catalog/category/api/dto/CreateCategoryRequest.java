package com.xdpsx.ecommerce.catalog.category.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;

/**
 * Admin create payload. Slug and display order are server-owned: the slug is generated from {@code name} and the
 * node is appended to the end of its sibling group.
 *
 * <p>{@code parentId} is part of the transitional CR1 contract and will move to a dedicated operation once
 * hierarchy changes are implemented properly.
 */
public record CreateCategoryRequest(
        @NotBlank @Size(max = 128) String name, CategoryStatus status, String imageId, Integer parentId) {}
