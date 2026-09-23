package com.xdpsx.ecommerce.catalog.brand.application;

import org.mapstruct.factory.Mappers;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.xdpsx.ecommerce.catalog.brand.api.dto.AdminBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.api.dto.BrandDetailResponse;
import com.xdpsx.ecommerce.catalog.brand.api.dto.BrandNoCatsDTO;
import com.xdpsx.ecommerce.catalog.brand.api.dto.CreateBrandRequest;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;

@Mapper
public interface BrandMapper {
    BrandMapper INSTANCE = Mappers.getMapper(BrandMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Brand toEntity(CreateBrandRequest request);

    @Mapping(target = "image", source = "entity.image.url")
    @Mapping(target = "categories", source = "entity.categories")
    AdminBrandResponse toAdminBrandResponse(Brand entity);

    @Mapping(target = "image", source = "entity.image")
    @Mapping(target = "categories", source = "entity.categories")
    BrandDetailResponse toBrandDetailResponse(Brand entity);

    BrandNoCatsDTO fromEntityToNotCatsDTO(Brand entity);
}
