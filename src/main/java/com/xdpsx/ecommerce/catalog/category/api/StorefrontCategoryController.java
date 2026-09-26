package com.xdpsx.ecommerce.catalog.category.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.StorefrontCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;

import lombok.RequiredArgsConstructor;

/**
 * Storefront read boundary for Category. Reads are public and never expose
 * admin-only fields.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class StorefrontCategoryController implements StorefrontCategoryApiDocs {
    private final CategoryService categoryService;

    @GetMapping
    public List<StorefrontCategoryResponse> getRootCategories() {
        return categoryService.getStorefrontRootCategories();
    }

    @GetMapping("/tree")
    public List<CategoryTreeResponse> getCategoryTree() {
        return categoryService.getCategoryTree();
    }

    /**
     * The literal {@code /tree} mapping wins over this template, so a slug of
     * {@code tree} is not reachable; that
     * slug is reserved by the literal route.
     */
    @GetMapping("/{slug}")
    public StorefrontCategoryResponse getCategoryBySlug(@PathVariable String slug) {
        return categoryService.getStorefrontCategoryBySlug(slug);
    }
}
