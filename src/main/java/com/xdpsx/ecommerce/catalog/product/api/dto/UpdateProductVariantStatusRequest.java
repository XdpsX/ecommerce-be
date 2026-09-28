package com.xdpsx.ecommerce.catalog.product.api.dto;

import jakarta.validation.constraints.NotNull;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;

public record UpdateProductVariantStatusRequest(@NotNull ProductVariantStatus status) {}
