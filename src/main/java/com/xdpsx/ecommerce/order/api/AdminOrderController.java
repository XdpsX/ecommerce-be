package com.xdpsx.ecommerce.order.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.order.api.dto.CancellationRequest;
import com.xdpsx.ecommerce.order.api.dto.OrderDTO;
import com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO;
import com.xdpsx.ecommerce.order.api.dto.OrderStatusUpdate;
import com.xdpsx.ecommerce.order.application.OrderCancellationService;
import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;

import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminOrderController implements AdminOrderApi {
    private final OrderCancellationService orderCancellationService;
    private final OrderService orderService;

    @PostMapping("/admin/orders/{orderId}/cancellation")
    public ResponseEntity<OrderDetailsDTO> cancel(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody CancellationRequest request) {
        return ResponseEntity.ok(orderCancellationService.cancelAsAdmin(orderId, request, authentication.getName()));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderDetailsDTO> getOrderById(@PathVariable Long orderId) {
        OrderDetailsDTO response = orderService.getOrderById(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders")
    public ResponseEntity<PageResponse<OrderDTO>> getPageOrders(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String trackingNumber) {
        PageResponse<OrderDTO> response =
                orderService.getAllOrders(pageNum, pageSize, orderStatus, paymentStatus, trackingNumber);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<OrderDTO> updateOrderStatus(
            @PathVariable Long id, @Valid @RequestBody OrderStatusUpdate request) {
        OrderDTO response = orderService.updateOrderStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
