package com.xdpsx.ecommerce.checkout.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.xdpsx.ecommerce.checkout.api.dto.CheckoutRequest;
import com.xdpsx.ecommerce.checkout.api.dto.CheckoutResponse;
import com.xdpsx.ecommerce.common.error.ApiProblemSchema;
import com.xdpsx.ecommerce.common.error.ValidationProblemSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Checkout API")
interface CheckoutApi {
    @Operation(
            summary = "Create checkout",
            description =
                    "Creates a checkout on the first request and returns the existing result for an idempotent replay.",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Idempotent replay",
                        content = @Content(schema = @Schema(implementation = CheckoutResponse.class))),
                @ApiResponse(
                        responseCode = "201",
                        description = "Checkout created",
                        content = @Content(schema = @Schema(implementation = CheckoutResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation or checkout request error",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        oneOf = {ValidationProblemSchema.class, ApiProblemSchema.class
                                                        }))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Authentication required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Address not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Checkout conflict",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "502",
                        description = "Payment initialization failed",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ResponseEntity<CheckoutResponse> checkout(
            Authentication authentication,
            @Parameter(
                            name = "Idempotency-Key",
                            description = "Stable key used to safely retry the same checkout request",
                            required = true,
                            in = ParameterIn.HEADER)
                    String idempotencyKey,
            @Valid CheckoutRequest request,
            HttpServletRequest httpServletRequest);
}
