package com.xdpsx.ecommerce.catalog.product.api.dto;

import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;

public record AdminProductCategoryResponse(
        Integer id, String name, String slug, CategoryStatus status, boolean effectivelyActive) {}
