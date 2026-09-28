package com.xdpsx.ecommerce.catalog.product.application;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.catalog.product.domain.Product;

@Mapper(componentModel = "spring")
public abstract class ProductMapper {
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "variants", ignore = true)
    public abstract Product fromCreateRequestToEntity(ProductCreateRequest request);

    @Mapping(target = "images", ignore = true)
    @Mapping(target = "variants", ignore = true)
    public abstract Product fromUpdateRequestToEntity(ProductUpdateRequest request);

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

    @Mapping(target = "mainImage", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "options", ignore = true)
    @Mapping(target = "variants", ignore = true)
    protected abstract ProductDetailsDTO toDetailsDTO(Product entity);

    public ProductDetailsDTO fromEntityToDetailsDTO(Product entity) {
        ProductDetailsDTO dto = toDetailsDTO(entity);
        dto.setMainImage(entity.getImages().stream()
                .filter(image -> image.getDisplayOrder() == 0)
                .map(image -> image.getMedia().getUrl())
                .findFirst()
                .orElse(null));
        dto.setImages(entity.getImages().stream()
                .map(image -> new ProductImageDTO(
                        image.getMedia().getId(), image.getMedia().getUrl(), image.getDisplayOrder()))
                .collect(java.util.stream.Collectors.toList()));
        return dto;
    }
}
