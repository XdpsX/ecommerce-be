package com.xdpsx.ecommerce.cart.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartQuantityRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Cart API")
interface CartApi {
    @Operation(summary = "Get cart", responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<CartResponse> getCart(Authentication authentication, HttpServletRequest request);

    @Operation(summary = "Add cart item", responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<CartResponse> addItem(
            @Valid CartItemRequest request, Authentication authentication, HttpServletRequest httpRequest);

    @Operation(
            summary = "Replace cart item quantity",
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<CartResponse> replaceItem(
            Long variantId,
            @Valid CartQuantityRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest);

    @Operation(summary = "Remove cart item", responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<CartResponse> removeItem(
            Long variantId, Authentication authentication, HttpServletRequest httpRequest);

    @Operation(summary = "Claim guest cart", responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<CartResponse> claim(Authentication authentication, HttpServletRequest request);
}
