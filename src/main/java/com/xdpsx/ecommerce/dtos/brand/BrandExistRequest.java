package com.xdpsx.ecommerce.dtos.brand;

import jakarta.validation.constraints.NotBlank;

public record BrandExistRequest(@NotBlank String name) {}
