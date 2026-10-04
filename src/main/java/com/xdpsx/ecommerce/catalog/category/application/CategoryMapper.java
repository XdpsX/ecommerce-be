package com.xdpsx.ecommerce.catalog.category.application;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xdpsx.ecommerce.catalog.category.api.dto.AdminCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategorySummaryResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.StorefrontCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.domain.Category;

@Mapper
public interface CategoryMapper {
    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    @Mapping(target = "image", source = "entity.image.url")
    @Mapping(target = "parent", source = "entity.parent")
    @Mapping(target = "effectivelyActive", expression = "java(entity.isEffectivelyActive())")
    AdminCategoryResponse toAdminCategoryResponse(Category entity);

    @Mapping(target = "children", ignore = true)
    @Mapping(target = "image", source = "entity.image.url")
    CategoryTreeResponse toCategoryTreeResponse(Category entity);

    @Mapping(target = "image", source = "entity.image.url")
    StorefrontCategoryResponse toStorefrontCategoryResponse(Category entity);

    CategorySummaryResponse toSummaryResponse(Category entity);
}
