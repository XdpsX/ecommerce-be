package com.xdpsx.ecommerce.order.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.xdpsx.ecommerce.common.pagination.PageConstants;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.order.api.dto.CancellationRequest;
import com.xdpsx.ecommerce.order.api.dto.OrderDTO;
import com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order API")
interface OrderApi {
    @Operation(summary = "Create payment attempt", responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<InitPaymentResponse> createPaymentAttempt(
            Authentication authentication, Long orderId, HttpServletRequest request);

    @Operation(summary = "List current user orders", responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<PageResponse<OrderDTO>> getMyOrders(
            Authentication authentication,
            @Min(1) int pageNum,
            @Min(1) @Max(PageConstants.MAX_ITEMS_PER_PAGE) int pageSize);

    @Operation(
            summary = "Get order by tracking number",
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<OrderDetailsDTO> getOrderByTrackNumber(Authentication authentication, String trackingNumber);

    @Operation(summary = "Cancel order", responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<OrderDetailsDTO> cancel(
            Authentication authentication, Long orderId, @Valid CancellationRequest request);
}
