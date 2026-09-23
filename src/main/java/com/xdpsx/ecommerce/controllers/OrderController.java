package com.xdpsx.ecommerce.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.dtos.common.PageResponse;
import com.xdpsx.ecommerce.dtos.order.*;
import com.xdpsx.ecommerce.entities.enums.OrderStatus;
import com.xdpsx.ecommerce.entities.enums.PaymentStatus;
import com.xdpsx.ecommerce.services.OrderService;
import com.xdpsx.ecommerce.utils.RequestUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/create")
    public ResponseEntity<OrderResponse> placeOrder(
            Authentication authentication,
            @Valid @RequestBody OrderRequest orderRequest,
            HttpServletRequest httpServletRequest) {
        var ipAddress = RequestUtil.getIpAddress(httpServletRequest);
        orderRequest.setIpAddress(ipAddress);
        return new ResponseEntity<>(
                orderService.placeOrder(authentication.getName(), orderRequest), HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<PageResponse<OrderDTO>> getMyOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize) {
        PageResponse<OrderDTO> response = orderService.getMyOrders(authentication.getName(), pageNum, pageSize);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/code/{trackingNumber}")
    public ResponseEntity<OrderDetailsDTO> getOrderByTrackNumber(
            Authentication authentication, @PathVariable String trackingNumber) {
        OrderDetailsDTO response = orderService.getOrderByTrackingNumber(authentication.getName(), trackingNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailsDTO> getOrderById(@PathVariable Long orderId) {
        OrderDetailsDTO response = orderService.getOrderById(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderDTO>> getPageOrders(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus) {
        PageResponse<OrderDTO> response = orderService.getAllOrders(pageNum, pageSize, orderStatus, paymentStatus);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDTO> updateOrderStatus(
            @PathVariable Long id, @Valid @RequestBody OrderStatusUpdate request) {
        OrderDTO response = orderService.updateOrderStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
