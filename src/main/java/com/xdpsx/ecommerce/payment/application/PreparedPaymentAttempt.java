package com.xdpsx.ecommerce.payment.application;

import java.math.BigDecimal;
import java.time.Instant;

public record PreparedPaymentAttempt(
        Long orderId,
        Long userId,
        String providerReference,
        BigDecimal expectedAmount,
        String currency,
        Instant expiresAt) {}
