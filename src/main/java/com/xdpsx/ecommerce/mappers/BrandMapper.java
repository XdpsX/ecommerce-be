package com.xdpsx.ecommerce.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xdpsx.ecommerce.dtos.brand.AdminBrandResponse;
import com.xdpsx.ecommerce.dtos.brand.BrandDetailResponse;
import com.xdpsx.ecommerce.dtos.brand.BrandNoCatsDTO;
import com.xdpsx.ecommerce.dtos.brand.CreateBrandRequest;
import com.xdpsx.ecommerce.entities.Brand;

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
