package com.xdpsx.ecommerce.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;

import com.xdpsx.ecommerce.auth.api.dto.LoginRequest;
import com.xdpsx.ecommerce.auth.api.dto.RegisterRequest;
import com.xdpsx.ecommerce.auth.api.dto.TokenResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Authentication API")
interface AuthApi {
    @Operation(summary = "Register", responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<TokenResponse> register(@Valid RegisterRequest request);

    @Operation(summary = "Login", responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<TokenResponse> login(@Valid LoginRequest request);

    @Operation(summary = "Refresh access token", responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<TokenResponse> refresh(HttpServletRequest request);

    @Operation(summary = "Logout", responses = @ApiResponse(responseCode = "204", description = "No Content"))
    ResponseEntity<Void> logout(HttpServletRequest request);
}
