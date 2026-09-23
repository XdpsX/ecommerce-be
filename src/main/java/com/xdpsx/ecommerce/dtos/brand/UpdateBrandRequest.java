package com.xdpsx.ecommerce.dtos.brand;

import java.time.LocalDateTime;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBrandRequest(
        @NotBlank @Size(max = 64) String name,
        boolean publicFlg,
        String imageId,
        Set<Integer> categoryIds,
        @NotNull LocalDateTime lastRetrievedAt) {}
