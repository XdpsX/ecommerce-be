package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;

public record AdminProductSummaryResponse(
        Long id,
        String name,
        String slug,
        BigDecimal price,
        BigDecimal discountedPrice,
        double discountPercent,
        boolean inStock,
        boolean published,
        String mainImage,
        AdminProductCategoryResponse category,
        AdminProductBrandResponse brand) {}
