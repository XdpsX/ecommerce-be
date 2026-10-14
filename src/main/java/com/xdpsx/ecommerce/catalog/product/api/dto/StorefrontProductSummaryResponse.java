package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;

import com.xdpsx.ecommerce.catalog.brand.api.dto.BrandNoCatsDTO;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategorySummaryResponse;

import io.swagger.v3.oas.annotations.media.Schema;

public record StorefrontProductSummaryResponse(
        Long id,
        String name,
        String slug,
        BigDecimal minimumPrice,
        BigDecimal maximumPrice,
        String currency,
        boolean inStock,

        @Schema(description = "Product-card image URL; the original URL is used while eager processing is not READY.")
        String mainImage,

        CategorySummaryResponse category,
        BrandNoCatsDTO brand) {}
