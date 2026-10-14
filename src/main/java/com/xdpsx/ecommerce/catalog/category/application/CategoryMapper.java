package com.xdpsx.ecommerce.catalog.category.application;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xdpsx.ecommerce.catalog.category.api.dto.AdminCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategorySummaryResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.StorefrontCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.media.application.MediaUrlResolver;
import com.xdpsx.ecommerce.media.application.storage.MediaVariant;

@Mapper
public interface CategoryMapper {
    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    @Mapping(target = "image", source = "entity.image.url")
    @Mapping(target = "parent", source = "entity.parent")
    @Mapping(target = "effectivelyActive", expression = "java(entity.isEffectivelyActive())")
    AdminCategoryResponse toAdminCategoryResponse(Category entity);

    default CategoryTreeResponse toCategoryTreeResponse(Category entity, MediaUrlResolver mediaUrlResolver) {
        CategoryTreeResponse response = new CategoryTreeResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setSlug(entity.getSlug());
        response.setImage(mediaUrlResolver.resolve(entity.getImage(), MediaVariant.CATEGORY_CARD));
        return response;
    }

    default StorefrontCategoryResponse toStorefrontCategoryResponse(
            Category entity, MediaUrlResolver mediaUrlResolver) {
        return new StorefrontCategoryResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                mediaUrlResolver.resolve(entity.getImage(), MediaVariant.CATEGORY_CARD));
    }

    CategorySummaryResponse toSummaryResponse(Category entity);
}
