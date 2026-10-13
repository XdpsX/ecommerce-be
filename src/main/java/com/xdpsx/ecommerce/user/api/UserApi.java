package com.xdpsx.ecommerce.user.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.xdpsx.ecommerce.user.api.dto.UpdateUserProfileRequest;
import com.xdpsx.ecommerce.user.api.dto.UserProfile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User API")
interface UserApi {
    @Operation(
            summary = "Get current user",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<UserProfile> getCurrentUser(Authentication authentication);

    @Operation(
            summary = "Update current user",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<UserProfile> updateCurrentUser(
            Authentication authentication, @Valid UpdateUserProfileRequest request);
}
