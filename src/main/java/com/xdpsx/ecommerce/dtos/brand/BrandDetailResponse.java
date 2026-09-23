package com.xdpsx.ecommerce.dtos.brand;

import java.util.List;

import com.xdpsx.ecommerce.dtos.media.ViewMediaDTO;

public record BrandDetailResponse(
        Integer id, String name, boolean publicFlg, ViewMediaDTO image, List<CategoryDTO> categories) {
    public record CategoryDTO(Integer id, String name) {}
}
