package com.xdpsx.ecommerce.catalog.brand.api.dto;

import java.util.List;

import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;

public record AdminBrandResponse(
        Integer id, String name, BrandStatus status, Long version, ViewMediaDTO image, List<CategoryDTO> categories) {
    public record CategoryDTO(Integer id, String name) {}
}
