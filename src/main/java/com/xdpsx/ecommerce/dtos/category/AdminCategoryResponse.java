package com.xdpsx.ecommerce.dtos.category;

public record AdminCategoryResponse(Integer id, String name, boolean publicFlg, String image, CategoryDTO parent) {
    public record CategoryDTO(Integer id, String name) {}
}
