package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductVariantCreateRequest(
        @NotBlank @Size(max = 128) String sku,
        @Size(max = 128) String barcode,
        @NotNull @Size(max = 16) List<@NotNull Long> optionValueIds) {}
