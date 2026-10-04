package com.xdpsx.ecommerce.catalog.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class ProductVariantSalePricingTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void resolvePriceAt_ShouldUseStartInclusiveAndEndExclusiveInterval() {
        ProductVariant variant = variant();
        Instant starts = NOW.plusSeconds(60);
        Instant ends = NOW.plusSeconds(120);
        variant.replaceSaleSchedule(new BigDecimal("80.00"), starts, ends, NOW);

        assertThat(variant.resolvePriceAt(starts).finalUnitPrice()).isEqualByComparingTo("80.00");
        assertThat(variant.resolvePriceAt(ends.minusNanos(1)).finalUnitPrice()).isEqualByComparingTo("80.00");
        assertThat(variant.resolvePriceAt(ends).finalUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(variant.resolvePriceAt(NOW).discountAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void scheduleAndBasePriceChanges_ShouldProtectMoneyInvariants() {
        ProductVariant variant = variant();
        assertThatThrownBy(() -> variant.replaceSaleSchedule(new BigDecimal("100.00"), NOW, NOW.plusSeconds(60), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> variant.replaceSaleSchedule(new BigDecimal("80.001"), NOW, NOW.plusSeconds(60), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        variant.replaceSaleSchedule(new BigDecimal("80.00"), NOW, NOW.plusSeconds(60), NOW);
        assertThatThrownBy(() -> variant.changeBasePrice(new BigDecimal("80.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(variant.getBasePrice()).isEqualByComparingTo("100.00");
    }

    private static ProductVariant variant() {
        return ProductVariant.builder()
                .id(1L)
                .sku("SKU-1")
                .basePrice(new BigDecimal("100.00"))
                .status(ProductVariantStatus.ACTIVE)
                .combinationKey("")
                .build();
    }
}
