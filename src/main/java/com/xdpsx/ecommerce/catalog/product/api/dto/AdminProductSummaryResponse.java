package com.xdpsx.ecommerce.catalog.product.api.dto;

public record AdminProductSummaryResponse(
        Long id,
        String name,
        String slug,
        boolean inStock,
        boolean published,
        String mainImage,
        AdminProductCategoryResponse category,
        AdminProductBrandResponse brand) {}
