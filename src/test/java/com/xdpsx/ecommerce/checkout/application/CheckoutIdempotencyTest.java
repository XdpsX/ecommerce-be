package com.xdpsx.ecommerce.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.checkout.api.dto.CheckoutRequest;
import com.xdpsx.ecommerce.common.error.ApplicationException;

class CheckoutIdempotencyTest {
    @Test
    void requestHash_ShouldIgnoreDescriptionWhitespaceButIncludeAddress() {
        CheckoutRequest first = new CheckoutRequest();
        first.setAddressId(10L);
        first.setDescription("  office  ");
        CheckoutRequest same = new CheckoutRequest();
        same.setAddressId(10L);
        same.setDescription("office");
        CheckoutRequest different = new CheckoutRequest();
        different.setAddressId(11L);
        different.setDescription("office");

        assertThat(CheckoutIdempotency.requestHash(first)).isEqualTo(CheckoutIdempotency.requestHash(same));
        assertThat(CheckoutIdempotency.requestHash(first)).isNotEqualTo(CheckoutIdempotency.requestHash(different));
    }

    @Test
    void key_ShouldRejectBlankAndOverlongValues() {
        assertThatThrownBy(() -> CheckoutIdempotency.normalizeKey("  ")).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> CheckoutIdempotency.normalizeKey("x".repeat(129)))
                .isInstanceOf(ApplicationException.class);
        assertThat(CheckoutIdempotency.normalizeKey("  checkout-1  ")).isEqualTo("checkout-1");
    }
}
