package com.xdpsx.ecommerce.catalog.category.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Admin payload for moving one category inside the hierarchy.
 *
 * <p>{@code parentId} is nullable on purpose: {@code null} moves the category to the root group. {@code position} is
 * the zero-based index the category must occupy in the target sibling group after the move.
 */
public record MoveCategoryRequest(
        @Positive Integer parentId, @NotNull @Min(0) Integer position) {}
