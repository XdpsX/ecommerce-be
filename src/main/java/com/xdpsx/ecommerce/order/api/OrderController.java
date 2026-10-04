package com.xdpsx.ecommerce.order.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.common.pagination.PageConstants;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.order.api.dto.*;
import com.xdpsx.ecommerce.order.application.OrderCancellationService;
import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;
import com.xdpsx.ecommerce.payment.application.PaymentAttemptService;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final OrderCancellationService orderCancellationService;
    private final PaymentAttemptService paymentAttemptService;

    @PostMapping("/{orderId}/payment-attempts")
    public ResponseEntity<InitPaymentResponse> createPaymentAttempt(
            Authentication authentication, @PathVariable Long orderId, HttpServletRequest request) {
        return ResponseEntity.ok(
                paymentAttemptService.initialize(authentication.getName(), orderId, request.getRemoteAddr()));
    }

    @GetMapping("/me")
    public ResponseEntity<PageResponse<OrderDTO>> getMyOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "5") @Min(1) @Max(PageConstants.MAX_ITEMS_PER_PAGE) int pageSize) {
        PageResponse<OrderDTO> response = orderService.getMyOrders(authentication.getName(), pageNum, pageSize);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/code/{trackingNumber}")
    public ResponseEntity<OrderDetailsDTO> getOrderByTrackNumber(
            Authentication authentication, @PathVariable String trackingNumber) {
        OrderDetailsDTO response = orderService.getOrderByTrackingNumber(authentication.getName(), trackingNumber);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancellation")
    public ResponseEntity<OrderDetailsDTO> cancel(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody CancellationRequest request) {
        return ResponseEntity.ok(
                orderCancellationService.cancelForCustomer(authentication.getName(), orderId, request));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderDetailsDTO> getOrderById(@PathVariable Long orderId) {
        OrderDetailsDTO response = orderService.getOrderById(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<OrderDTO>> getPageOrders(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "5") @Min(1) @Max(PageConstants.MAX_ITEMS_PER_PAGE) int pageSize,
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String trackingNumber) {
        PageResponse<OrderDTO> response =
                orderService.getAllOrders(pageNum, pageSize, orderStatus, paymentStatus, trackingNumber);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderDTO> updateOrderStatus(
            @PathVariable Long id, @Valid @RequestBody OrderStatusUpdate request) {
        OrderDTO response = orderService.updateOrderStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
