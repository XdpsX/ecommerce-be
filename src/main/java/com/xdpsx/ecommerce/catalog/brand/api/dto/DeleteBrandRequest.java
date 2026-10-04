package com.xdpsx.ecommerce.catalog.brand.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Delete boundary for Brand. Uses the entity {@code version} as the optimistic-concurrency token instead of a
 * client timestamp; a stale or missing version is rejected before any media or association state changes.
 */
public record DeleteBrandRequest(@NotNull Long version) {}
