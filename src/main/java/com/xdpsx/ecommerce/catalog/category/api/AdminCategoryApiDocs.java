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
 * <p>{@code parentId} on create and update is documented as a transitional CR1 contract: it keeps hierarchy
 * management possible before the dedicated move/reorder operations exist.
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
                    + "end of its sibling group. parentId is a transitional CR1 contract and will be replaced "
                    + "by a dedicated hierarchy operation.",
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
                            + "to change it. parentId is a transitional CR1 contract and will be replaced by a "
                            + "dedicated hierarchy operation.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error / Max depth exceeded / Malformed slug",
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
