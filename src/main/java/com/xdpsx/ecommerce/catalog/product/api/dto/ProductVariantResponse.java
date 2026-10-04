package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;

public record ProductVariantResponse(
        Long id,
        String sku,
        String barcode,
        BigDecimal basePrice,
        BigDecimal salePrice,
        Instant saleStartsAt,
        Instant saleEndsAt,
        BigDecimal discountAmount,
        BigDecimal finalUnitPrice,
        String currency,
        ProductVariantStatus status,
        List<Long> optionValueIds) {
    public ProductVariantResponse(
            Long id,
            String sku,
            String barcode,
            BigDecimal basePrice,
            String currency,
            ProductVariantStatus status,
            List<Long> optionValueIds) {
        this(
                id,
                sku,
                barcode,
                basePrice,
                null,
                null,
                null,
                BigDecimal.ZERO.setScale(2),
                basePrice,
                currency,
                status,
                optionValueIds);
    }
}
