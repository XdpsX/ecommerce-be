package com.xdpsx.ecommerce.catalog.category.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeFilter;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeResponse;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;

import lombok.RequiredArgsConstructor;

/**
 * Storefront read boundary for Category. Reads are public and never expose admin-only fields.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class StorefrontCategoryController implements StorefrontCategoryApiDocs {
    private final CategoryService categoryService;

    @GetMapping("/tree")
    public List<CategoryTreeResponse> getCategoryTree(@Valid CategoryTreeFilter filter) {
        return categoryService.getCategoryTree(filter);
    }
}
