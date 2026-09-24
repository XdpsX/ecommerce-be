package com.xdpsx.ecommerce.catalog.category.api;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.catalog.category.api.dto.*;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;
import com.xdpsx.ecommerce.catalog.shared.api.dto.CheckExistResponse;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CategoryController implements CategoryControllerApi {
    private final CategoryService categoryService;

    @GetMapping("/admin/categories")
    public PageResponse<AdminCategoryResponse> getAdminCategories(@Valid AdminCategoryFilter filter) {
        return categoryService.getAdminCategories(filter);
    }

    @GetMapping("/categories/{category-id}")
    public AdminCategoryResponse getCategory(@PathVariable("category-id") Integer categoryId) {
        return categoryService.getCategory(categoryId);
    }

    @GetMapping("/categories/tree")
    public List<CategoryTreeResponse> getCategoryTree(@Valid CategoryTreeFilter filter) {
        return categoryService.getCategoryTree(filter);
    }

    @PostMapping("/categories/create")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse data = categoryService.createCategory(request);
        return ResponseEntity.created(URI.create("/categories/" + data.getId())).body(data);
    }

    @PostMapping("/categories/exists")
    public CheckExistResponse checkCategoryExist(@Valid @RequestBody CategoryExistRequest request) {
        return categoryService.checkCategoryExist(request);
    }

    @PutMapping("/categories/{id}/update")
    public CategoryResponse updateCategory(
            @PathVariable Integer id, @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Integer id, @Valid @RequestBody ModifyExclusiveDTO request) {
        categoryService.deleteCategory(id, request);
    }
}
