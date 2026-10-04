package com.xdpsx.ecommerce.catalog.brand.api.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;

public record UpdateBrandRequest(
        @NotBlank @Size(max = 64) String name,
        @NotNull BrandStatus status,
        String imageId,
        Set<Integer> categoryIds,
        @NotNull Long version) {}
