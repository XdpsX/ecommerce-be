package com.xdpsx.ecommerce.catalog.product.api;

import java.util.Map;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.common.error.ApiProblemSchema;
import com.xdpsx.ecommerce.common.error.ValidationProblemSchema;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Product API")
interface AdminProductApiDocs {
    @Operation(
            summary = "Get admin products",
            description = "Retrieve all products, including drafts and unpublished products.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {@ApiResponse(responseCode = "401", description = "Authentication required")})
    PageResponse<AdminProductSummaryResponse> getProducts(@ParameterObject AdminProductFilter filter);

    @Operation(
            summary = "Get admin product by ID",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    AdminProductDetailResponse getProduct(Long id);

    @Operation(
            summary = "Create a product draft",
            description = "Create a product. Publication is a separate operation and new products are drafts.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "201", description = "Created"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content = @Content(schema = @Schema(implementation = ValidationProblemSchema.class)))
            })
    ResponseEntity<AdminProductSummaryResponse> createProduct(ProductCreateRequest request);

    @Operation(summary = "Update product metadata", security = @SecurityRequirement(name = "Bearer Authorization"))
    AdminProductSummaryResponse updateProduct(Long id, ProductUpdateRequest request);

    @Operation(summary = "Delete a product", security = @SecurityRequirement(name = "Bearer Authorization"))
    void deleteProduct(Long id);

    @Operation(
            summary = "Update product publication",
            description = "Publish or unpublish a product through the dedicated lifecycle operation.",
            security = @SecurityRequirement(name = "Bearer Authorization"))
    AdminProductDetailResponse updatePublication(Long id, UpdateProductPublicationRequest request);

    @Operation(
            summary = "Check product slug availability",
            security = @SecurityRequirement(name = "Bearer Authorization"))
    Map<String, Boolean> getSlugAvailability(String slug);
}
