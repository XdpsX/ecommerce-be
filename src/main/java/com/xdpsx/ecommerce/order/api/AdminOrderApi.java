package com.xdpsx.ecommerce.order.api;

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
import com.xdpsx.ecommerce.order.api.dto.OrderStatusUpdate;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;

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

    @Operation(
            summary = "Get admin order by ID",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<OrderDetailsDTO> getOrderById(Long orderId);

    @Operation(
            summary = "List admin orders",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<PageResponse<OrderDTO>> getPageOrders(
            @Min(1) int pageNum,
            @Min(1) @Max(PageConstants.MAX_ITEMS_PER_PAGE) int pageSize,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            String trackingNumber);

    @Operation(
            summary = "Update admin order status",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    ResponseEntity<OrderDTO> updateOrderStatus(Long id, @Valid OrderStatusUpdate request);
}
