package com.xdpsx.ecommerce.catalog.category.api.dto;

public record AdminCategoryResponse(Integer id, String name, boolean publicFlg, String image, CategoryDTO parent) {
    public record CategoryDTO(Integer id, String name) {}
}
