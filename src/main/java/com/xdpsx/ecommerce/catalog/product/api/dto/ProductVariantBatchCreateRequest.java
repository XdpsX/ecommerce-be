package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ProductVariantBatchCreateRequest(
        @NotEmpty @Size(max = 100) List<@Valid ProductVariantCreateRequest> variants) {}
