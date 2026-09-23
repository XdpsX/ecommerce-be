package com.xdpsx.ecommerce.cart.application;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.catalog.product.application.ProductMapper;

@Mapper(componentModel = "spring")
public abstract class CartItemMapper {
    @Autowired
    private ProductMapper productMapper;

    @Mapping(target = "product", ignore = true)
    abstract CartItemResponse toResponse(CartItem entity);

    public CartItemResponse fromEntityToResponse(CartItem entity) {
        CartItemResponse response = toResponse(entity);
        response.setProduct(productMapper.fromEntityToResponse(entity.getProduct()));
        return response;
    }
}
