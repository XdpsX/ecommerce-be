package com.xdpsx.ecommerce.catalog.category.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Storefront tree node. Read-only projection of a Category; admin-only fields are deliberately absent.
 */
@Getter
@Setter
public class CategoryTreeResponse {
    private Integer id;
    private String name;
    private String slug;
    private String image;
    private List<CategoryTreeResponse> children;
}
