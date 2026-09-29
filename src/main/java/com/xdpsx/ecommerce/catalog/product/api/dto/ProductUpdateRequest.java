package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;
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

    @Min(value = 0)
    @Max(value = 1_000_000_000)
    private BigDecimal price;

    private double discountPercent;
    private boolean inStock;

    @Size(max = 4096)
    private String description;

    private Integer categoryId;
    private Integer brandId;

    @Size(max = 5)
    private List<String> imageIds;
}
