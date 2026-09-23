package com.xdpsx.ecommerce.catalog.category.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryExistRequest(@NotBlank String name) {}
