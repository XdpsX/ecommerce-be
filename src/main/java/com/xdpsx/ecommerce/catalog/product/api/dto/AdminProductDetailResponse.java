package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminProductDetailResponse(
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
        AdminProductBrandResponse brand,
        String description,
        List<ProductImageDTO> images,
        List<ProductOptionResponse> options,
        List<ProductVariantSelectionResponse> variants) {}
