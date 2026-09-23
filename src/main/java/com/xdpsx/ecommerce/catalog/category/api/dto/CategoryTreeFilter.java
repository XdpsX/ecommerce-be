package com.xdpsx.ecommerce.catalog.category.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.xdpsx.ecommerce.catalog.category.domain.Category;

public record CategoryTreeFilter(
        @Min(1) @Max(Category.MAX_DEPTH) Integer maxLevel, String sort) {}
