package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.xdpsx.ecommerce.catalog.brand.api.dto.BrandNoCatsDTO;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategorySummaryResponse;

import io.swagger.v3.oas.annotations.media.Schema;

public record StorefrontProductDetailResponse(
        Long id,
        String name,
        String slug,
        BigDecimal minimumPrice,
        BigDecimal maximumPrice,
        String currency,
        boolean inStock,

        @Schema(
                description =
                        "Product-detail main image URL; the original URL is used until eager processing is READY.")
        String mainImage,

        CategorySummaryResponse category,
        BrandNoCatsDTO brand,

        @Schema(
                description =
                        "Product description returned as stored; embedded image URLs are not rewritten or validated. Clients should use productContent from Media upload for newly embedded images.")
        String description,

        List<ProductImageDTO> images,
        List<ProductOptionResponse> options,
        List<ProductVariantSelectionResponse> variants) {}
