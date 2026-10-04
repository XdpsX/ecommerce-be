package com.xdpsx.ecommerce.order.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.order.api.dto.CancellationRequest;
import com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO;
import com.xdpsx.ecommerce.order.application.OrderCancellationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminOrderController {
    private final OrderCancellationService orderCancellationService;

    @PostMapping("/{orderId}/cancellation")
    public ResponseEntity<OrderDetailsDTO> cancel(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody CancellationRequest request) {
        return ResponseEntity.ok(orderCancellationService.cancelAsAdmin(orderId, request, authentication.getName()));
    }
}
