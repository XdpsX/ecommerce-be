package com.xdpsx.ecommerce.inventory.api;

import org.springframework.security.core.Authentication;

import com.xdpsx.ecommerce.common.error.ApiProblemSchema;
import com.xdpsx.ecommerce.common.error.ValidationProblemSchema;
import com.xdpsx.ecommerce.inventory.api.dto.InventoryAdjustmentRequest;
import com.xdpsx.ecommerce.inventory.api.dto.InventoryBalanceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Inventory API")
interface AdminInventoryApiDocs {
    @Operation(
            summary = "Get SKU inventory balance",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Variant inventory balance not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    InventoryBalanceResponse getBalance(Long variantId);

    @Operation(
            summary = "Adjust SKU on-hand inventory",
            description = "Apply an auditable quantity delta to one SKU.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content = @Content(schema = @Schema(implementation = ValidationProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Variant inventory balance not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Adjustment would violate the balance invariant",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    InventoryBalanceResponse adjustOnHand(
            Long variantId, InventoryAdjustmentRequest request, Authentication authentication);
}
