package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductVariantSelectionResponse(
        Long variantId,
        String sku,
        List<Long> optionValueIds,
        BigDecimal basePrice,
        String currency,
        boolean available) {}
