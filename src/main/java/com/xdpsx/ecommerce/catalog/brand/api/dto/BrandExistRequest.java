package com.xdpsx.ecommerce.catalog.brand.api.dto;

import jakarta.validation.constraints.NotBlank;

public record BrandExistRequest(@NotBlank String name) {}
