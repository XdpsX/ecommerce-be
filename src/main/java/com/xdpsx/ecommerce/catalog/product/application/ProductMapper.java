package com.xdpsx.ecommerce.catalog.product.application;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.xdpsx.ecommerce.catalog.brand.api.dto.BrandNoCatsDTO;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategorySummaryResponse;
import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.catalog.product.domain.Product;

@Mapper(componentModel = "spring")
public abstract class ProductMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "published", constant = "false")
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "brand", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "variants", ignore = true)
    public abstract Product fromCreateRequestToEntity(ProductCreateRequest request);

    @Mapping(target = "mainImage", ignore = true)
    @Mapping(target = "discountedPrice", expression = "java(entity.getDiscountedPrice())")
    protected abstract ProductResponse toResponse(Product entity);

    public ProductResponse fromEntityToResponse(Product entity) {
        ProductResponse response = toResponse(entity);
        response.setMainImage(entity.getImages().stream()
                .filter(image -> image.getDisplayOrder() == 0)
                .map(image -> image.getMedia().getUrl())
                .findFirst()
                .orElse(null));
        return response;
    }

    public StorefrontProductSummaryResponse toStorefrontSummary(Product entity) {
        return new StorefrontProductSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getPrice(),
                entity.getDiscountedPrice(),
                entity.getDiscountPercent(),
                entity.isInStock(),
                mainImage(entity),
                category(entity),
                brand(entity));
    }

    public AdminProductSummaryResponse toAdminSummary(Product entity) {
        return new AdminProductSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getPrice(),
                entity.getDiscountedPrice(),
                entity.getDiscountPercent(),
                entity.isInStock(),
                entity.isPublished(),
                mainImage(entity),
                adminCategory(entity),
                adminBrand(entity));
    }

    public StorefrontProductDetailResponse toStorefrontDetail(
            Product entity, List<ProductOptionResponse> options, List<ProductVariantSelectionResponse> variants) {
        return new StorefrontProductDetailResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getPrice(),
                entity.getDiscountedPrice(),
                entity.getDiscountPercent(),
                entity.isInStock(),
                mainImage(entity),
                category(entity),
                brand(entity),
                entity.getDescription(),
                imageDtos(entity),
                options,
                variants);
    }

    public AdminProductDetailResponse toAdminDetail(
            Product entity, List<ProductOptionResponse> options, List<ProductVariantSelectionResponse> variants) {
        return new AdminProductDetailResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getPrice(),
                entity.getDiscountedPrice(),
                entity.getDiscountPercent(),
                entity.isInStock(),
                entity.isPublished(),
                mainImage(entity),
                adminCategory(entity),
                adminBrand(entity),
                entity.getDescription(),
                imageDtos(entity),
                options,
                variants);
    }

    private static String mainImage(Product entity) {
        return entity.getImages().stream()
                .filter(image -> image.getDisplayOrder() == 0)
                .map(image -> image.getMedia().getUrl())
                .findFirst()
                .orElse(null);
    }

    private static java.util.List<ProductImageDTO> imageDtos(Product entity) {
        return entity.getImages().stream()
                .map(image -> new ProductImageDTO(
                        image.getMedia().getId(), image.getMedia().getUrl(), image.getDisplayOrder()))
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
