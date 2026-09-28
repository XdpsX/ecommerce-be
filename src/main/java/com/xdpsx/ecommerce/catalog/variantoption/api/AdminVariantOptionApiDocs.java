package com.xdpsx.ecommerce.catalog.variantoption.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;

import com.xdpsx.ecommerce.catalog.variantoption.api.dto.*;
import com.xdpsx.ecommerce.common.error.ApiProblemSchema;
import com.xdpsx.ecommerce.common.error.ValidationProblemSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Variant Option API")
interface AdminVariantOptionApiDocs {
    @Operation(
            summary = "List variant options",
            description = "Retrieve all catalog options and their values in display order, including inactive entries",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "403", description = "Admin role required")
            })
    List<VariantOptionResponse> getVariantOptions();

    @Operation(
            summary = "Get variant option",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    VariantOptionResponse getVariantOption(Long id);

    @Operation(
            summary = "Create variant option",
            description = "Create an option and optionally its ordered values. Codes are trimmed and lowercased.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "201", description = "Created"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content = @Content(schema = @Schema(implementation = ValidationProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Code or display order already exists",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ResponseEntity<VariantOptionResponse> createVariantOption(@Valid CreateVariantOptionRequest request);

    @Operation(
            summary = "Update variant option",
            description =
                    "Update display metadata or lifecycle. The canonical code is immutable; an option in use by an active Variant cannot be deactivated.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Display order already exists or an active Variant still references the option",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    VariantOptionResponse updateVariantOption(Long id, @Valid UpdateVariantOptionRequest request);

    @Operation(
            summary = "Add variant option value",
            description = "Add a value to an option. Omitting displayOrder appends it after existing values.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "201", description = "Created"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Option not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Code or display order already exists",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ResponseEntity<VariantOptionValueResponse> addValue(Long optionId, @Valid CreateVariantOptionValueRequest request);

    @Operation(
            summary = "Update variant option value",
            description =
                    "Update value label, order or lifecycle. The canonical code is immutable; a value in use by an active Variant cannot be deactivated.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Option or value not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Display order already exists or an active Variant still references the value",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    VariantOptionValueResponse updateValue(Long optionId, Long valueId, @Valid UpdateVariantOptionValueRequest request);

    @Operation(
            summary = "Reorder variant option values",
            description =
                    "Replace the complete value order in one transaction; direct swaps are safe under the unique order constraint.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "400",
                        description = "The request must contain every value exactly once",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Option not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    List<VariantOptionValueResponse> reorderValues(Long optionId, @Valid ReorderVariantOptionValuesRequest request);
}
