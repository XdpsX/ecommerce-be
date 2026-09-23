package com.xdpsx.ecommerce.catalog.category.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryTreeResponse {
    private Integer id;
    private String name;
    private List<CategoryTreeResponse> children;
}
