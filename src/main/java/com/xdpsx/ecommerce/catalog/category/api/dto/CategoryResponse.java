package com.xdpsx.ecommerce.catalog.category.api.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {
    private Integer id;
    private String name;

    @Builder.Default
    private List<CategoryResponse> children = new ArrayList<>();
}
