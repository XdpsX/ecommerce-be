package com.xdpsx.ecommerce.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;

class CartItemMapperTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void fromEntityToResponse_ShouldExposeResolvedSaleMoney() {
        Product product = Product.builder().id(11L).name("Shirt").slug("shirt").build();
        ProductVariant variant = ProductVariant.builder()
                .id(101L)
                .product(product)
                .sku("SHIRT-BLACK")
                .basePrice(new BigDecimal("100.00"))
                .status(ProductVariantStatus.ACTIVE)
                .combinationKey("shirt-black")
                .build();
        variant.replaceSaleSchedule(new BigDecimal("80.00"), NOW.minusSeconds(1), NOW.plusSeconds(3600), NOW);
        CartItem item = CartItem.builder()
                .id(new CartItemId(7L, variant.getId()))
                .variant(variant)
                .quantity(2)
                .build();

        CartItemResponse response = new CartItemMapper() {}.fromEntityToResponse(item, NOW);

        assertThat(response.getBasePrice()).isEqualByComparingTo("100.00");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(response.getFinalUnitPrice()).isEqualByComparingTo("80.00");
        assertThat(response.getCurrency()).isEqualTo("VND");
    }
}
