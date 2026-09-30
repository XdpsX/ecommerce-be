package com.xdpsx.ecommerce.cart.application;

import java.util.List;

import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.config.StorePricingProperties;

@Mapper(componentModel = "spring")
public abstract class CartItemMapper {
    @Autowired
    private StorePricingProperties pricingProperties;

    public CartItemResponse fromEntityToResponse(CartItem entity) {
        var variant = entity.getVariant();
        var product = variant == null ? null : variant.getProduct();
        CartItemResponse response = new CartItemResponse();
        response.setProductId(product == null ? null : product.getId());
        response.setProductName(product == null ? null : product.getName());
        response.setVariantId(variant == null ? entity.getId().getVariantId() : variant.getId());
        response.setSku(variant == null ? null : variant.getSku());
        response.setOptionValueIds(
                variant == null
                        ? List.of()
                        : variant.getSelections().stream()
                                .map(s -> s.getOptionValueId())
                                .toList());
        response.setQuantity(entity.getQuantity());
        response.setBasePrice(variant == null ? null : variant.getBasePrice());
        response.setCurrency(pricingProperties == null ? "VND" : pricingProperties.getCurrency());
        response.setAvailable(entity.isAvailable());
        return response;
    }
}
