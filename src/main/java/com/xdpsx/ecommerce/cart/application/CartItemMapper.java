package com.xdpsx.ecommerce.cart.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.cart.api.dto.CartAvailability;
import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.config.StorePricingProperties;

@Component
public class CartItemMapper {
    private final StorePricingProperties pricingProperties;
    private final Clock pricingClock;

    public CartItemMapper(StorePricingProperties pricingProperties, Clock pricingClock) {
        this.pricingProperties = pricingProperties;
        this.pricingClock = pricingClock;
    }

    public CartItemResponse fromEntityToResponse(CartItem entity) {
        return fromEntityToResponse(entity, pricingClock.instant());
    }

    @SuppressWarnings("deprecation") // Keep the deprecated `available` JSON alias in sync for contract compatibility.
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
        response.setEstimatedSubtotal(
                resolved == null || entity.getQuantity() == null
                        ? null
                        : resolved.finalUnitPrice().multiply(java.math.BigDecimal.valueOf(entity.getQuantity())));
        response.setCurrency(currency());
        response.setAvailability(
                entity.getAvailability() == null
                        ? (entity.isAvailable() ? CartAvailability.AVAILABLE : CartAvailability.UNAVAILABLE)
                        : CartAvailability.valueOf(entity.getAvailability()));
        response.setAvailable(entity.isAvailable());
        return response;
    }

    private String currency() {
        if (pricingProperties.getCurrency() == null) return "VND";
        return pricingProperties.getCurrency();
    }
}
