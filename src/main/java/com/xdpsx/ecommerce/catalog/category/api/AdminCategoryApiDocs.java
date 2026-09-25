package com.xdpsx.ecommerce.catalog.category.api;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

import com.xdpsx.ecommerce.catalog.category.api.dto.*;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.error.ApiProblemSchema;
import com.xdpsx.ecommerce.common.error.ValidationProblemSchema;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Documentation-only interface for the admin Category boundary. Route mappings and parameter binding stay on
 * {@link AdminCategoryController}, which requires the {@code ADMIN} role.
 *
 * <p>Metadata/lifecycle, hierarchy and order are documented as separate operations: update never changes the parent,
 * {@code /{id}/parent} moves one node and {@code /order} replaces a whole sibling group order.
 */
@Tag(name = "Admin Category API")
interface AdminCategoryApiDocs {
    @Operation(
            summary = "Get admin categories",
            description = "Retrieve a paginated flat list of categories, including inactive ones",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ValidationProblemSchema.class)))
            })
    PageResponse<AdminCategoryResponse> getAdminCategories(@ParameterObject AdminCategoryFilter filter);

    @Operation(
            summary = "Get admin category by ID",
            description = "Retrieve a single category regardless of its status",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    AdminCategoryResponse getAdminCategory(Integer id);

    @Operation(
            summary = "Create a category",
            description = "Create a category. The slug is generated from the name and the category is appended to the "
                    + "end of its sibling group. parentId selects that group; null appends to the root group.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "201", description = "Created"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error / Max depth exceeded / Empty normalized slug",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        oneOf = {ValidationProblemSchema.class, ApiProblemSchema.class
                                                        }))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Category name or slug already exists",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ResponseEntity<AdminCategoryResponse> createCategory(@Valid CreateCategoryRequest request);

    @Operation(
            summary = "Update a category",
            description =
                    "Update name, status, slug and image. Renaming does not change the slug; send slug explicitly "
                            + "to change it. The parent and the sibling order are not touched by this operation.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error / Malformed slug",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        oneOf = {ValidationProblemSchema.class, ApiProblemSchema.class
                                                        }))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Category name or slug already exists / Concurrent modification",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    AdminCategoryResponse updateCategory(Integer id, @Valid UpdateCategoryRequest request);

    @Operation(
            summary = "Move a category",
            description =
                    "Move a category to another parent and/or position. parentId null moves it to the root group. "
                            + "position is the zero-based index in the target group after the move. Self-parenting, moving a "
                            + "node under its own descendant and a move that would exceed the maximum depth are rejected.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error / Invalid parent / Invalid position / Max depth exceeded",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        oneOf = {ValidationProblemSchema.class, ApiProblemSchema.class
                                                        }))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Category or target parent not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Concurrent hierarchy write, the request can be retried",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    AdminCategoryResponse moveCategory(Integer id, @Valid MoveCategoryRequest request);

    @Operation(
            summary = "Reorder a sibling group",
            description = "Replace the display order of a whole sibling group. parentId null selects the root group. "
                    + "categoryIds must be the exact set of that group, without duplicates; the request order becomes "
                    + "the new display order. Partial reorders are rejected so the client reloads after a conflict.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "204", description = "No Content"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error / Category list is not the exact sibling group",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        oneOf = {ValidationProblemSchema.class, ApiProblemSchema.class
                                                        }))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Parent not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Concurrent hierarchy write, the request can be retried",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    void reorderCategories(@Valid ReorderCategoriesRequest request);

    @Operation(
            summary = "Delete a category",
            description = "Hard delete. Rejected while the category still has children, products or brands",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "204", description = "No Content"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Category still in use / Concurrent modification",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    void deleteCategory(Integer id, @Valid ModifyExclusiveDTO request);
}
