package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.util.List;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class ProductUpdateRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String slug;

    @Size(max = 4096)
    private String description;

    private Integer categoryId;
    private Integer brandId;

    @Size(max = 5)
    private List<String> imageIds;
}
