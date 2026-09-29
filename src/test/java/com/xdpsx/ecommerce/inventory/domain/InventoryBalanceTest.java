package com.xdpsx.ecommerce.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;

class InventoryBalanceTest {
    @Test
    void adjustment_ShouldDeriveAvailableFromOnHandAndReserved() {
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-10").build();
        InventoryBalance balance = InventoryBalance.zero(variant);

        balance.adjustOnHand(12);

        assertThat(balance.getOnHand()).isEqualTo(12);
        assertThat(balance.getReserved()).isZero();
        assertThat(balance.available()).isEqualTo(12);
    }

    @Test
    void adjustment_ShouldRejectNegativeResult() {
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-10").build();
        InventoryBalance balance = InventoryBalance.zero(variant);
        balance.adjustOnHand(3);

        assertThatThrownBy(() -> balance.adjustOnHand(-4)).isInstanceOf(InventoryBalanceAdjustmentException.class);
        assertThat(balance.getOnHand()).isEqualTo(3);
    }
}
