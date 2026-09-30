package com.xdpsx.ecommerce.cart.application;

import java.time.Clock;
import java.time.Instant;
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

    @Autowired
    private Clock pricingClock;

    public CartItemResponse fromEntityToResponse(CartItem entity) {
        return fromEntityToResponse(entity, (pricingClock == null ? Clock.systemUTC() : pricingClock).instant());
    }

    public CartItemResponse fromEntityToResponse(CartItem entity, Instant now) {
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
        var resolved = variant == null ? null : variant.resolvePriceAt(now);
        response.setBasePrice(resolved == null ? null : resolved.basePrice());
        response.setDiscountAmount(resolved == null ? null : resolved.discountAmount());
        response.setFinalUnitPrice(resolved == null ? null : resolved.finalUnitPrice());
        response.setCurrency(currency());
        response.setAvailable(entity.isAvailable());
        return response;
    }

    private String currency() {
        if (pricingProperties == null || pricingProperties.getCurrency() == null) return "VND";
        return pricingProperties.getCurrency();
    }
}
