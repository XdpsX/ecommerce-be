package com.xdpsx.ecommerce.order.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.xdpsx.ecommerce.order.api.dto.CancellationRequest;
import com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Order API")
interface AdminOrderApi {
    @Operation(
            summary = "Cancel order as admin",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<OrderDetailsDTO> cancel(
            Authentication authentication, Long orderId, @Valid CancellationRequest request);
}
