package com.xdpsx.ecommerce.catalog.category.api;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.catalog.category.api.dto.*;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * Platform admin boundary for Category. Every endpoint requires the {@code ADMIN} role.
 *
 * <p>Metadata/lifecycle, hierarchy and order are separate operations: {@code PUT /{id}} never changes the parent,
 * {@code PUT /{id}/parent} moves one node and {@code PUT /order} replaces a whole sibling group order.
 */
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController implements AdminCategoryApiDocs {
    private final CategoryService categoryService;

    @GetMapping
    public PageResponse<AdminCategoryResponse> getAdminCategories(@Valid AdminCategoryFilter filter) {
        return categoryService.getAdminCategories(filter);
    }

    @GetMapping("/{id}")
    public AdminCategoryResponse getAdminCategory(@PathVariable Integer id) {
        return categoryService.getAdminCategory(id);
    }

    @PostMapping
    public ResponseEntity<AdminCategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        AdminCategoryResponse data = categoryService.createCategory(request);
        return ResponseEntity.created(URI.create("/admin/categories/" + data.id()))
                .body(data);
    }

    @PutMapping("/{id}")
    public AdminCategoryResponse updateCategory(
            @PathVariable Integer id, @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    @PutMapping("/{id}/parent")
    public AdminCategoryResponse moveCategory(
            @PathVariable Integer id, @Valid @RequestBody MoveCategoryRequest request) {
        return categoryService.moveCategory(id, request);
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderCategories(@Valid @RequestBody ReorderCategoriesRequest request) {
        categoryService.reorderCategories(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Integer id, @Valid @RequestBody ModifyExclusiveDTO request) {
        categoryService.deleteCategory(id, request);
    }
}
