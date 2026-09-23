package com.xdpsx.ecommerce.dtos.category;

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
