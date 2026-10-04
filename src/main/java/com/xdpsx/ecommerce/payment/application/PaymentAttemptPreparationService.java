package com.xdpsx.ecommerce.payment.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.config.PaymentAttemptProperties;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentAttempt;
import com.xdpsx.ecommerce.payment.domain.PaymentAttemptStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentAttemptPreparationService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentAttemptProperties properties;
    private final Clock clock;

    @Transactional
    public PreparedPaymentAttempt prepare(String userEmail, Long orderId) {
        User user = userRepository
                .findByEmail(EmailIdentity.canonicalize(userEmail))
                .orElseThrow(() -> notFound("user", userEmail));
        Order order = orderRepository
                .findByIdAndUserIdForUpdate(orderId, user.getId())
                .orElseThrow(() -> notFound("order", orderId));

        Instant now = clock.instant();
        validateOrder(order, now);
        Payment payment = order.getPayment();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "paymentNotPending"));
        }

        PaymentAttempt active = paymentAttemptRepository
                .findTopByPaymentIdAndStatusOrderByCreatedAtDesc(payment.getId(), PaymentAttemptStatus.PENDING)
                .orElse(null);
        if (active != null) {
            if (active.isActiveAt(now)) return toPrepared(order, user, active);
            active.markExpired(now);
        }

        Instant expiresAt = now.plus(properties.getLifetime());
        if (expiresAt.isAfter(order.getReservationExpiresAt())) expiresAt = order.getReservationExpiresAt();
        if (!expiresAt.isAfter(now)) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "reservationExpired"));
        }

        BigDecimal amount = order.getTotalAmount();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .payment(payment)
                .providerReference(UUID.randomUUID().toString())
                .status(PaymentAttemptStatus.PENDING)
                .expectedAmount(amount)
                .currency(order.getCurrency())
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();
        paymentAttemptRepository.save(attempt);
        return toPrepared(order, user, attempt);
    }

    private static void validateOrder(Order order, Instant now) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "orderNotPendingPayment"));
        }
        if (order.getReservationExpiresAt() == null
                || !order.getReservationExpiresAt().isAfter(now)) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "reservationExpired"));
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "invalidOrderAmount"));
        }
        if (!"VND".equals(order.getCurrency())) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "unsupportedCurrency"));
        }
        if (order.getPayment() == null) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "paymentMissing"));
        }
    }

    private static PreparedPaymentAttempt toPrepared(Order order, User user, PaymentAttempt attempt) {
        return new PreparedPaymentAttempt(
                order.getId(),
                user.getId(),
                attempt.getProviderReference(),
                attempt.getExpectedAmount(),
                attempt.getCurrency(),
                attempt.getExpiresAt());
    }

    private static ApplicationException notFound(String resourceType, Object resourceId) {
        return new ApplicationException(
                ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", resourceType, "resourceId", resourceId));
    }
}
