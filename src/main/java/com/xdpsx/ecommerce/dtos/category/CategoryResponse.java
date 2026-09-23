package com.xdpsx.ecommerce.dtos.category;

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
