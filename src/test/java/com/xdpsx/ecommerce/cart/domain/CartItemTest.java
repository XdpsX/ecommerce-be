package com.xdpsx.ecommerce.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CartItemTest {
    @Test
    void increaseAndReplace_ShouldKeepQuantityWithinTheCartInvariant() {
        CartItem item = CartItem.builder().quantity(2).build();

        item.increaseBy(3);
        item.replaceQuantity(99);

        assertThat(item.getQuantity()).isEqualTo(99);
    }

    @Test
    void increase_ShouldRejectOverflowWithoutChangingTheItem() {
        CartItem item = CartItem.builder().quantity(98).build();

        assertThatThrownBy(() -> item.increaseBy(2)).isInstanceOf(CartQuantityException.class);

        assertThat(item.getQuantity()).isEqualTo(98);
    }
}
