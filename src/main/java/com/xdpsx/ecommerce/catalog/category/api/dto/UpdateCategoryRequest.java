package com.xdpsx.ecommerce.catalog.category.api.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;

/**
 * Admin update payload.
 *
 * <p>Renaming does not change the slug. An explicit {@code slug} is accepted when the caller wants to change the
 * public identifier, and must already be in normalized form.
 *
 * <p>{@code parentId} is part of the transitional CR1 contract. It is kept so an admin does not lose the ability to
 * manage the hierarchy, but it still lacks cycle and subtree-height validation and will be replaced by a dedicated
 * operation.
 */
public record UpdateCategoryRequest(
        @NotBlank @Size(max = 128) String name,
        CategoryStatus status,

        @Size(max = 160) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")
        String slug,

        String imageId,
        Integer parentId,
        @NotNull LocalDateTime lastRetrievedAt) {}
