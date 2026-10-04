package com.xdpsx.ecommerce.refund.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xdpsx.ecommerce.refund.domain.RefundStatus;

public record RefundQueueItemResponse(
        Long id,
        Long orderId,
        String trackingNumber,
        RefundStatus status,
        BigDecimal amount,
        String currency,
        String reason,
        LocalDateTime requestedAt) {}
