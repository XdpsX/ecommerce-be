package com.xdpsx.ecommerce.catalog.brand.api.dto;

import java.util.List;

import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;

public record BrandDetailResponse(
        Integer id, String name, boolean publicFlg, ViewMediaDTO image, List<CategoryDTO> categories) {
    public record CategoryDTO(Integer id, String name) {}
}
