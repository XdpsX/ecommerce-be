package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.util.List;

import jakarta.validation.constraints.*;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCreateRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String slug;

    @Size(max = 4096)
    private String description;

    @NotNull
    private Integer categoryId;

    @NotNull
    private Integer brandId;

    @Size(max = 5)
    private List<String> imageIds;
}
