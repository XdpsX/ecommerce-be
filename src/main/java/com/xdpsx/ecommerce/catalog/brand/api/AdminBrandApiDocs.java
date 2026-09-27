package com.xdpsx.ecommerce.catalog.brand.api;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

import com.xdpsx.ecommerce.catalog.brand.api.dto.*;
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
 * Documentation-only interface for the admin Brand boundary. Route mappings and parameter binding stay on
 * {@link AdminBrandController}, which requires the {@code ADMIN} role.
 *
 * <p>Update and delete carry the entity {@code version} as the optimistic-concurrency token; a stale token is
 * rejected with 409 before any association or media state changes.
 */
@Tag(name = "Admin Brand API")
interface AdminBrandApiDocs {
    @Operation(
            summary = "Get admin brands",
            description = "Retrieve a paginated flat list of brands, including inactive ones",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ValidationProblemSchema.class)))
            })
    PageResponse<AdminBrandResponse> getAdminBrands(@ParameterObject AdminBrandFilter filter);

    @Operation(
            summary = "Get admin brand by ID",
            description = "Retrieve a single brand regardless of its status",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    AdminBrandResponse getAdminBrand(Integer id);

    @Operation(
            summary = "Create a brand",
            description = "Create a brand. categoryIds may be null or empty to create the brand before "
                    + "merchandising assigns it; every submitted id must reference an effectively active Category.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "201", description = "Created"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ValidationProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Category or media not found / not usable",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Brand name already exists",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ResponseEntity<AdminBrandResponse> createBrand(@Valid CreateBrandRequest request);

    @Operation(
            summary = "Update a brand",
            description = "Update name, status, image and category associations. categoryIds semantics: null keeps "
                    + "the current associations, an empty set clears them, a non-empty set replaces them. The request "
                    + "version must match the current entity version.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ValidationProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Brand, category or media not found / not usable",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Brand name already exists / Concurrent modification",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    AdminBrandResponse updateBrand(Integer id, @Valid UpdateBrandRequest request);

    @Operation(
            summary = "Delete a brand",
            description = "Delete a brand. The request version must match the current entity version. A brand that "
                    + "is still referenced by a Product cannot be deleted.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "204", description = "Deleted"),
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
                        description = "Concurrent modification / Brand is in use",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    void deleteBrand(Integer id, @Valid DeleteBrandRequest request);
}
