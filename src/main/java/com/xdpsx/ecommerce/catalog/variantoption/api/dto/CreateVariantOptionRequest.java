package com.xdpsx.ecommerce.catalog.variantoption.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;

public record CreateVariantOptionRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @PositiveOrZero Integer displayOrder,
        VariantOptionStatus status,
        @Valid List<CreateVariantOptionValueRequest> values) {}
