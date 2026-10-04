package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;

public record AdminProductSummaryResponse(
        Long id,
        String name,
        String slug,
        boolean inStock,
        boolean published,
        String mainImage,
        AdminProductCategoryResponse category,
        AdminProductBrandResponse brand,
        BigDecimal minimumPrice,
        BigDecimal maximumPrice,
        long onHand,
        long reserved,
        long available) {}
