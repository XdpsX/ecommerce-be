package com.xdpsx.ecommerce.catalog.brand.api.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBrandRequest(
        @NotBlank @Size(max = 64) String name, boolean publicFlg, String imageId, Set<Integer> categoryIds) {}
