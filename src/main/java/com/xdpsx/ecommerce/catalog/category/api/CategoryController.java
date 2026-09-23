package com.xdpsx.ecommerce.catalog.category.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.catalog.category.api.dto.*;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;
import com.xdpsx.ecommerce.catalog.shared.api.dto.CheckExistResponse;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.api.APIResponse;
import com.xdpsx.ecommerce.common.error.SMessage;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CategoryController implements CategoryControllerApi {
    private final CategoryService categoryService;

    @GetMapping("/admin/categories")
    public APIResponse<PageResponse<AdminCategoryResponse>> getAdminCategories(@Valid AdminCategoryFilter filter) {
        PageResponse<AdminCategoryResponse> data = categoryService.getAdminCategories(filter);
        return APIResponse.ok(data);
    }

    @GetMapping("/categories/{category-id}")
    public APIResponse<AdminCategoryResponse> getCategory(@PathVariable("category-id") Integer categoryId) {
        AdminCategoryResponse data = categoryService.getCategory(categoryId);
        return APIResponse.ok(data);
    }

    @GetMapping("/categories/tree")
    public APIResponse<List<CategoryTreeResponse>> getCategoryTree(@Valid CategoryTreeFilter filter) {
        List<CategoryTreeResponse> data = categoryService.getCategoryTree(filter);
        return APIResponse.ok(data);
    }

    @PostMapping("/categories/create")
    @ResponseStatus(HttpStatus.CREATED)
    public APIResponse<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse data = categoryService.createCategory(request);
        return new APIResponse<>(HttpStatus.CREATED, data, SMessage.CREATE_SUCCESSFULLY);
    }

    @PostMapping("/categories/exists")
    public APIResponse<CheckExistResponse> checkCategoryExist(@Valid @RequestBody CategoryExistRequest request) {
        CheckExistResponse data = categoryService.checkCategoryExist(request);
        return APIResponse.ok(data);
    }

    @PutMapping("/categories/{id}/update")
    public APIResponse<CategoryResponse> updateCategory(
            @PathVariable Integer id, @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryResponse data = categoryService.updateCategory(id, request);
        return APIResponse.ok(data, SMessage.UPDATE_SUCCESSFULLY);
    }

    @DeleteMapping("/categories/{id}/delete")
    public APIResponse<Void> deleteCategory(@PathVariable Integer id, @Valid @RequestBody ModifyExclusiveDTO request) {
        categoryService.deleteCategory(id, request);
        return APIResponse.noContent(SMessage.DELETE_SUCCESSFULLY);
    }
}
