package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;

public record ProductVariantResponse(
        Long id,
        String sku,
        String barcode,
        BigDecimal basePrice,
        String currency,
        ProductVariantStatus status,
        List<Long> optionValueIds) {}
