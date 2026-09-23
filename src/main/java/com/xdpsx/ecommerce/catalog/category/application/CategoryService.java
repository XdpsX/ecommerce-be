package com.xdpsx.ecommerce.catalog.category.application;

import java.util.List;

import com.xdpsx.ecommerce.catalog.category.api.dto.*;
import com.xdpsx.ecommerce.catalog.shared.api.dto.CheckExistResponse;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

public interface CategoryService {
    PageResponse<AdminCategoryResponse> getAdminCategories(AdminCategoryFilter filter);

    AdminCategoryResponse getCategory(Integer categoryId);

    List<CategoryTreeResponse> getCategoryTree(CategoryTreeFilter filter);

    CategoryResponse createCategory(CreateCategoryRequest request);

    CheckExistResponse checkCategoryExist(CategoryExistRequest request);

    CategoryResponse updateCategory(Integer id, UpdateCategoryRequest request);

    void deleteCategory(Integer id, ModifyExclusiveDTO request);
}
