package com.xdpsx.ecommerce.catalog.product.domain;

import java.math.BigDecimal;

/** Monetary values for one Variant at one captured instant. */
public record ResolvedVariantPrice(BigDecimal basePrice, BigDecimal discountAmount, BigDecimal finalUnitPrice) {}
