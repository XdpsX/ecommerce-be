package com.xdpsx.ecommerce.catalog.category.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Admin payload for reordering a whole sibling group.
 *
 * <p>{@code parentId} selects the group ({@code null} means the root group). {@code categoryIds} must be the exact set
 * of that group at write time, without duplicates; the request order becomes {@code displayOrder = 0..N-1}.
 */
public record ReorderCategoriesRequest(
        @Positive Integer parentId, @NotEmpty List<@NotNull @Positive Integer> categoryIds) {}
