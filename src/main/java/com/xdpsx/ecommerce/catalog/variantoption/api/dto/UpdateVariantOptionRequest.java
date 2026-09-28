package com.xdpsx.ecommerce.catalog.variantoption.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;

public record UpdateVariantOptionRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull @PositiveOrZero Integer displayOrder,
        @NotNull VariantOptionStatus status) {}
