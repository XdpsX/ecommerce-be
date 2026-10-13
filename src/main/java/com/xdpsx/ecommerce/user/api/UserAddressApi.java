package com.xdpsx.ecommerce.user.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.xdpsx.ecommerce.common.error.ApiProblemSchema;
import com.xdpsx.ecommerce.common.error.ValidationProblemSchema;
import com.xdpsx.ecommerce.user.api.dto.UserAddressRequest;
import com.xdpsx.ecommerce.user.api.dto.UserAddressResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Address API")
interface UserAddressApi {
    @Operation(
            summary = "List current user addresses",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "401",
                        description = "Authentication required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ResponseEntity<List<UserAddressResponse>> list(Authentication authentication);

    @Operation(
            summary = "Create a user address",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "201", description = "Created"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content = @Content(schema = @Schema(implementation = ValidationProblemSchema.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Authentication required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ResponseEntity<UserAddressResponse> create(Authentication authentication, @Valid UserAddressRequest request);

    @Operation(
            summary = "Replace a user address",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content = @Content(schema = @Schema(implementation = ValidationProblemSchema.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Authentication required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Address not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ResponseEntity<UserAddressResponse> replace(
            Authentication authentication, Long addressId, @Valid UserAddressRequest request);

    @Operation(
            summary = "Delete a user address",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = {
                @ApiResponse(responseCode = "204", description = "No Content"),
                @ApiResponse(
                        responseCode = "401",
                        description = "Authentication required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Address not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    ResponseEntity<Void> delete(Authentication authentication, Long addressId);
}
