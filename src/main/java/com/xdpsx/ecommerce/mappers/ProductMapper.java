package com.xdpsx.ecommerce.mappers;

import java.util.ArrayList;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import com.xdpsx.ecommerce.dtos.product.*;
import com.xdpsx.ecommerce.entities.Product;
import com.xdpsx.ecommerce.utils.CloudinaryUploader;

@Mapper(componentModel = "spring")
public abstract class ProductMapper {
    @Autowired
    private CloudinaryUploader uploader;

    @Mapping(target = "images", source = "request.images", ignore = true)
    public abstract Product fromCreateRequestToEntity(ProductCreateRequest request);

    @Mapping(target = "images", source = "request.images", ignore = true)
    public abstract Product fromUpdateRequestToEntity(ProductUpdateRequest request);

    @Mapping(target = "mainImage", ignore = true)
    @Mapping(target = "discountedPrice", expression = "java(entity.getDiscountedPrice())")
    protected abstract ProductResponse toResponse(Product entity);

    public ProductResponse fromEntityToResponse(Product entity) {
        ProductResponse response = toResponse(entity);
        response.setMainImage(uploader.getFileUrl(entity.getMainImage()));
        return response;
    }

    @Mapping(target = "mainImage", ignore = true)
    @Mapping(source = "entity.images", target = "images", ignore = true)
    protected abstract ProductDetailsDTO toDetailsDTO(Product entity);

    public ProductDetailsDTO fromEntityToDetailsDTO(Product entity) {
        ProductDetailsDTO dto = toDetailsDTO(entity);
        dto.setMainImage(uploader.getFileUrl(entity.getMainImage()));
        dto.setImages(new ArrayList<>());
        for (var image : entity.getImages()) {
            ProductImageDTO imageDTO = new ProductImageDTO(image.getId(), uploader.getFileUrl(image.getUrl()));
            dto.getImages().add(imageDTO);
        }
        return dto;
    }
}
