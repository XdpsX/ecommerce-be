package com.xdpsx.ecommerce.catalog.brand.application;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xdpsx.ecommerce.catalog.brand.api.dto.AdminBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.api.dto.BrandNoCatsDTO;
import com.xdpsx.ecommerce.catalog.brand.api.dto.CreateBrandRequest;
import com.xdpsx.ecommerce.catalog.brand.api.dto.StorefrontBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.media.application.MediaUrlResolver;
import com.xdpsx.ecommerce.media.application.storage.MediaVariant;

@Mapper
public interface BrandMapper {
    BrandMapper INSTANCE = Mappers.getMapper(BrandMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Brand toEntity(CreateBrandRequest request);

    @Mapping(target = "image", source = "entity.image")
    @Mapping(target = "categories", source = "entity.categories")
    AdminBrandResponse toAdminBrandResponse(Brand entity);

    default StorefrontBrandResponse toStorefrontBrandResponse(Brand entity, MediaUrlResolver mediaUrlResolver) {
        return new StorefrontBrandResponse(
                entity.getId(), entity.getName(), mediaUrlResolver.resolve(entity.getImage(), MediaVariant.BRAND_LOGO));
    }

    BrandNoCatsDTO fromEntityToNotCatsDTO(Brand entity);
}
