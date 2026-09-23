package com.xdpsx.ecommerce.catalog.category.api;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;

import com.xdpsx.ecommerce.catalog.category.api.dto.*;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.shared.api.dto.CheckExistResponse;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.api.APIResponse;
import com.xdpsx.ecommerce.common.error.ErrorDetailsDTO;
import com.xdpsx.ecommerce.common.error.ErrorDTO;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Category API")
public interface CategoryControllerApi {
    @Operation(
            summary = "Get admin categories (Admin)",
            description = "Retrieve a paginated list of admin categories",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OK",
                        content = @Content(schema = @Schema(implementation = GetAdminCategoriesVM.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDetailsDTO.class)))
            })
    APIResponse<PageResponse<AdminCategoryResponse>> getAdminCategories(@ParameterObject AdminCategoryFilter filter);

    @Operation(
            summary = "Get category by ID",
            description = "Retrieve a category by its ID",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OK",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = GetCategoryVM.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDTO.class)))
            })
    APIResponse<AdminCategoryResponse> getCategory(Integer categoryId);

    @Operation(
            summary = "Get category tree",
            description = "Retrieve the category in hierarchical structure",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OK",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = GetCategoryTreeVM.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDetailsDTO.class)))
            })
    APIResponse<List<CategoryTreeResponse>> getCategoryTree(CategoryTreeFilter filter);

    @Operation(
            summary = "Create a new category (Admin)",
            description = "Create a new category with the provided details",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Created",
                        content = @Content(schema = @Schema(implementation = CreateCategoryVM.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error / Max depth exceeded",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(oneOf = {ErrorDetailsDTO.class, ErrorDTO.class}))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDTO.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Category already exists",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDTO.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Media resource type is invalid",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDTO.class)))
            })
    APIResponse<CategoryResponse> createCategory(CreateCategoryRequest request);

    @Operation(
            summary = "Check if category exists (Admin)",
            description = "Check if a category with the given name exists",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OK",
                        content = @Content(schema = @Schema(implementation = CheckCategoryExistVM.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDetailsDTO.class)))
            })
    APIResponse<CheckExistResponse> checkCategoryExist(CategoryExistRequest request);

    @Operation(
            summary = "Update category (Admin)",
            description = "Update the details of an existing category",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OK",
                        content = @Content(schema = @Schema(implementation = UpdateCategoryVM.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error / Max depth exceeded",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(oneOf = {ErrorDetailsDTO.class, ErrorDTO.class}))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDTO.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Category already exists / Modify exclusive",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDTO.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Media resource type is invalid",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDTO.class)))
            })
    APIResponse<CategoryResponse> updateCategory(Integer id, UpdateCategoryRequest request);

    @Operation(
            summary = "Delete category (Admin)",
            description = "Delete an existing category by its ID",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OK",
                        content = @Content(schema = @Schema(implementation = DeleteCategoryVM.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDetailsDTO.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Modify exclusive / Category in use",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorDTO.class))),
            })
    APIResponse<Void> deleteCategory(Integer id, ModifyExclusiveDTO request);

    class GetAdminCategoriesVM extends APIResponse<PageResponse<AdminCategoryResponse>> {}

    class GetCategoryVM extends APIResponse<AdminCategoryResponse> {}

    class GetCategoryTreeVM extends APIResponse<List<CategoryTreeResponse>> {}

    class CreateCategoryVM extends APIResponse<CategoryResponse> {}

    class CheckCategoryExistVM extends APIResponse<CheckExistResponse> {}

    class UpdateCategoryVM extends APIResponse<CategoryResponse> {}

    class DeleteCategoryVM extends APIResponse<Void> {}
}
