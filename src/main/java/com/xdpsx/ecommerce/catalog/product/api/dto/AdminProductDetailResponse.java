package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.util.List;

public record AdminProductDetailResponse(
        Long id,
        String name,
        String slug,
        boolean inStock,
        boolean published,
        String mainImage,
        AdminProductCategoryResponse category,
        AdminProductBrandResponse brand,
        String description,
        List<ProductImageDTO> images,
        List<ProductOptionResponse> options,
        List<ProductVariantSelectionResponse> variants) {}
