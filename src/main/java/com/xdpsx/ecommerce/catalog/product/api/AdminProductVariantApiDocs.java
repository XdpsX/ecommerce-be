package com.xdpsx.ecommerce.catalog.product.api;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.common.error.ApiProblemSchema;
import com.xdpsx.ecommerce.common.error.ValidationProblemSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Product Variant API")
interface AdminProductVariantApiDocs {
    @Operation(
            summary = "List product variants",
            description = "List active and inactive SKUs for one Product.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Product not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    List<ProductVariantResponse> getVariants(Long productId);

    @Operation(
            summary = "Create product variants",
            description = "Create one or more active SKUs atomically. SKU and option combinations are immutable.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "201", description = "Created"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content = @Content(schema = @Schema(implementation = ValidationProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "SKU, barcode or combination already exists",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ResponseEntity<List<ProductVariantResponse>> createVariants(
            Long productId, ProductVariantBatchCreateRequest request);

    @Operation(
            summary = "Update product variant barcode",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Variant not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Barcode already exists",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ProductVariantResponse updateBarcode(Long productId, Long variantId, UpdateProductVariantBarcodeRequest request);

    @Operation(
            summary = "Update product variant base price",
            description = "Change the base price of one SKU without changing its identity or status.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content = @Content(schema = @Schema(implementation = ValidationProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Variant not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ProductVariantResponse updatePrice(Long productId, Long variantId, UpdateProductVariantPriceRequest request);

    @Operation(
            summary = "Update product variant status",
            description = "Activate or deactivate a SKU. Deactivation never deletes the SKU.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content = @Content(schema = @Schema(implementation = ValidationProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Published product must retain an active SKU",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ProductVariantResponse updateStatus(Long productId, Long variantId, UpdateProductVariantStatusRequest request);
}
