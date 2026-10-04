package com.xdpsx.ecommerce.catalog.category.api.dto;

import jakarta.validation.constraints.NotNull;

/** Delete boundary for Category. Uses the entity version as its optimistic-concurrency token. */
public record DeleteCategoryRequest(@NotNull Long version) {}
