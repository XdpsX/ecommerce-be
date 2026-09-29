package com.xdpsx.ecommerce.inventory.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InventoryAdjustmentTest {
    @Test
    void constructor_ShouldRejectBlankPerformedBy() {
        assertThatThrownBy(() -> new InventoryAdjustment(10L, 1L, "receipt", "  ", 1L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
