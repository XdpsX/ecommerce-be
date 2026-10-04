package com.xdpsx.ecommerce.refund.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record RefundResponse(
        Long id,
        Long paymentId,
        Long orderId,
        String status,
        BigDecimal amount,
        String currency,
        String reason,
        String requestedBy,
        LocalDateTime requestedAt,
        String processedBy,
        LocalDateTime completedAt,
        String externalReference,
        String failureReason) {}
