package com.xdpsx.ecommerce.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StorePricingPropertiesTest {
    @Test
    void currency_ShouldRejectValuesUnsupportedByVnPay() {
        StorePricingProperties properties = new StorePricingProperties();

        assertThatThrownBy(() -> properties.setCurrency("USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be VND");
    }
}
