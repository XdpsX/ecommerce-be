package com.xdpsx.ecommerce.dtos.category;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 128) String name,
        boolean publicFlg,
        String imageId,
        Integer parentId,
        @NotNull LocalDateTime lastRetrievedAt) {}
