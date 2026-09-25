package com.xdpsx.ecommerce.catalog.category.application;

import java.util.List;

import com.xdpsx.ecommerce.catalog.category.api.dto.*;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

/**
 * Category use cases.
 *
 * <p>Admin operations and storefront reads are named explicitly so a call site cannot accidentally use admin
 * semantics for a public read, or vice versa.
 */
public interface CategoryService {
    PageResponse<AdminCategoryResponse> getAdminCategories(AdminCategoryFilter filter);

    AdminCategoryResponse getAdminCategory(Integer categoryId);

    List<CategoryTreeResponse> getCategoryTree(CategoryTreeFilter filter);

    AdminCategoryResponse createCategory(CreateCategoryRequest request);

    AdminCategoryResponse updateCategory(Integer id, UpdateCategoryRequest request);

    /**
     * Moves a category to another sibling group or to another position inside its current group.
     *
     * @return the moved category with its new parent and order
     */
    AdminCategoryResponse moveCategory(Integer id, MoveCategoryRequest request);

    /**
     * Replaces the order of a whole sibling group. The request must contain exactly the IDs of that group.
     */
    void reorderCategories(ReorderCategoriesRequest request);

    void deleteCategory(Integer id, ModifyExclusiveDTO request);
}
