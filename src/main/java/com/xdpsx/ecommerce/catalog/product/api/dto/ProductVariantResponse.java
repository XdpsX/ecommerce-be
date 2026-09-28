package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.util.List;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;

public record ProductVariantResponse(
        Long id, String sku, String barcode, ProductVariantStatus status, List<Long> optionValueIds) {}
