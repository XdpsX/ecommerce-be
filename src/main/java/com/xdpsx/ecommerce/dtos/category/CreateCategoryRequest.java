package com.xdpsx.ecommerce.dtos.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 128) String name, boolean publicFlg, String imageId, Integer parentId) {}
