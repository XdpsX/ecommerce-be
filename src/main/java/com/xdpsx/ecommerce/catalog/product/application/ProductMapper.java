package com.xdpsx.ecommerce.catalog.product.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.catalog.brand.api.dto.BrandNoCatsDTO;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategorySummaryResponse;
import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.config.StorePricingProperties;
import com.xdpsx.ecommerce.media.application.delivery.MediaUrlResolver;
import com.xdpsx.ecommerce.media.application.storage.MediaVariant;

@Component
public class ProductMapper {
    private final StorePricingProperties storePricingProperties;
    private final MediaUrlResolver mediaUrlResolver;

    public ProductMapper(StorePricingProperties storePricingProperties, MediaUrlResolver mediaUrlResolver) {
        this.storePricingProperties = storePricingProperties;
        this.mediaUrlResolver = mediaUrlResolver;
    }

    public Product fromCreateRequestToEntity(ProductCreateRequest request) {
        return Product.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .published(false)
                .build();
    }

    public StorefrontProductSummaryResponse toStorefrontSummary(
            Product entity, boolean inStock, java.math.BigDecimal minimumPrice, java.math.BigDecimal maximumPrice) {
        return new StorefrontProductSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                minimumPrice,
                maximumPrice,
                currency(),
                inStock,
                mainImage(entity, MediaVariant.PRODUCT_CARD),
                category(entity),
                brand(entity));
    }

    public AdminProductSummaryResponse toAdminSummary(Product entity) {
        return toAdminSummary(entity, false);
    }

    public AdminProductSummaryResponse toAdminSummary(Product entity, boolean inStock) {
        return toAdminSummary(entity, inStock, null, null, 0, 0, 0);
    }

    public AdminProductSummaryResponse toAdminSummary(
            Product entity,
            boolean inStock,
            java.math.BigDecimal minimumPrice,
            java.math.BigDecimal maximumPrice,
            long onHand,
            long reserved,
            long available) {
        return new AdminProductSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                inStock,
                entity.isPublished(),
                mainImage(entity),
                adminCategory(entity),
                adminBrand(entity),
                minimumPrice,
                maximumPrice,
                onHand,
                reserved,
                available);
    }

    public StorefrontProductDetailResponse toStorefrontDetail(
            Product entity,
            java.math.BigDecimal minimumPrice,
            java.math.BigDecimal maximumPrice,
            List<ProductOptionResponse> options,
            List<ProductVariantSelectionResponse> variants,
            boolean inStock) {
        return new StorefrontProductDetailResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                minimumPrice,
                maximumPrice,
                currency(),
                inStock,
                mainImage(entity, MediaVariant.PRODUCT_DETAIL),
                category(entity),
                brand(entity),
                entity.getDescription(),
                storefrontImageDtos(entity, MediaVariant.PRODUCT_DETAIL),
                options,
                variants);
    }

    public AdminProductDetailResponse toAdminDetail(
            Product entity, List<ProductOptionResponse> options, List<ProductVariantSelectionResponse> variants) {
        return toAdminDetail(entity, options, variants, false);
    }

    public AdminProductDetailResponse toAdminDetail(
            Product entity,
            List<ProductOptionResponse> options,
            List<ProductVariantSelectionResponse> variants,
            boolean inStock) {
        return new AdminProductDetailResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                inStock,
                entity.isPublished(),
                mainImage(entity),
                adminCategory(entity),
                adminBrand(entity),
                entity.getDescription(),
                imageDtos(entity),
                options,
                variants);
    }

    private String mainImage(Product entity, MediaVariant variant) {
        return entity.getImages().stream()
                .filter(image -> image.getDisplayOrder() == 0)
                .map(image -> mediaUrlResolver.resolve(image.getMedia(), variant))
                .findFirst()
                .orElse(null);
    }

    private static String mainImage(Product entity) {
        return entity.getImages().stream()
                .filter(image -> image.getDisplayOrder() == 0)
                .map(image -> image.getMedia().getUrl())
                .findFirst()
                .orElse(null);
    }

    private String currency() {
        return storePricingProperties == null ? "VND" : storePricingProperties.getCurrency();
    }

    private static java.util.List<ProductImageDTO> imageDtos(Product entity) {
        return entity.getImages().stream()
                .map(image -> new ProductImageDTO(
                        image.getMedia().getId(), image.getMedia().getUrl(), image.getDisplayOrder()))
                .toList();
    }

    private java.util.List<ProductImageDTO> storefrontImageDtos(Product entity, MediaVariant variant) {
        return entity.getImages().stream()
                .map(image -> new ProductImageDTO(
                        image.getMedia().getId(),
                        mediaUrlResolver.resolve(image.getMedia(), variant),
                        image.getDisplayOrder()))
                .toList();
    }

    private static CategorySummaryResponse category(Product entity) {
        return entity.getCategory() == null
                ? null
                : new CategorySummaryResponse(
                        entity.getCategory().getId(),
                        entity.getCategory().getName(),
                        entity.getCategory().getSlug());
    }

    private static BrandNoCatsDTO brand(Product entity) {
        return entity.getBrand() == null
                ? null
                : new BrandNoCatsDTO(
                        entity.getBrand().getId(), entity.getBrand().getName());
    }

    private static AdminProductCategoryResponse adminCategory(Product entity) {
        return entity.getCategory() == null
                ? null
                : new AdminProductCategoryResponse(
                        entity.getCategory().getId(),
                        entity.getCategory().getName(),
                        entity.getCategory().getSlug(),
                        entity.getCategory().getStatus(),
                        entity.getCategory().isEffectivelyActive());
    }

    private static AdminProductBrandResponse adminBrand(Product entity) {
        return entity.getBrand() == null
                ? null
                : new AdminProductBrandResponse(
                        entity.getBrand().getId(),
                        entity.getBrand().getName(),
                        entity.getBrand().getStatus());
    }
}
