package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;

import com.xdpsx.ecommerce.catalog.brand.api.dto.BrandNoCatsDTO;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategorySummaryResponse;

public record StorefrontProductSummaryResponse(
        Long id,
        String name,
        String slug,
        BigDecimal price,
        BigDecimal discountedPrice,
        double discountPercent,
        boolean inStock,
        String mainImage,
        CategorySummaryResponse category,
        BrandNoCatsDTO brand) {}
