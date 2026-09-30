package com.xdpsx.ecommerce.catalog.category.api.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;

/**
 * Admin update payload. Covers metadata and lifecycle only; the hierarchy is changed through the dedicated move and
 * reorder operations.
 *
 * <p>Renaming does not change the slug. An explicit {@code slug} is accepted when the caller wants to change the
 * public identifier, and must already be in normalized form.
 */
public record UpdateCategoryRequest(
        @NotBlank @Size(max = 128) String name,
        CategoryStatus status,

        @Size(max = 160) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")
        String slug,

        String imageId,
        @NotNull LocalDateTime lastRetrievedAt) {}
