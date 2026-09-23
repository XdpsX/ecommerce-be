package com.xdpsx.ecommerce.dtos.category;

import jakarta.validation.constraints.NotBlank;

public record CategoryExistRequest(@NotBlank String name) {}
