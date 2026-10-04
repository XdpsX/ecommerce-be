package com.xdpsx.ecommerce.payment.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.order.application.PaymentCallbackResult;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.PaymentAttempt;
import com.xdpsx.ecommerce.payment.domain.PaymentAttemptStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentAttemptCallbackService {
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final Clock clock;

    @Transactional
    public PaymentCallbackResult process(
            String providerReference,
            BigDecimal amount,
            boolean successful,
            String providerTransactionId,
            String responseCode) {
        PaymentAttempt reference = paymentAttemptRepository
                .findByProviderReferenceWithPaymentAndOrder(providerReference)
                .orElseThrow(() -> notFound("paymentAttempt", providerReference));
        Order order = reference.getPayment().getOrder();
        Long orderId = order.getId();

        Order lockedOrder = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> notFound("order", orderId));
        PaymentAttempt attempt = paymentAttemptRepository
                .findByProviderReferenceForUpdate(providerReference)
                .orElseThrow(() -> notFound("paymentAttempt", providerReference));

        if (attempt.getExpectedAmount() == null
                || amount == null
                || attempt.getExpectedAmount().compareTo(amount) != 0
                || !"VND".equals(attempt.getCurrency())) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST);
        }
        if (attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
            return PaymentCallbackResult.ALREADY_CONFIRMED;
        }
        if (!successful) {
            if (attempt.getStatus() != PaymentAttemptStatus.PENDING) return PaymentCallbackResult.CONFIRMED;
            attempt.markFailed(responseCode, clock.instant());
            return PaymentCallbackResult.CONFIRMED;
        }
        if (providerTransactionId == null || providerTransactionId.isBlank() || providerTransactionId.length() > 32) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST);
        }

        if (lockedOrder.getPayment() != null && lockedOrder.getPayment().getStatus() == PaymentStatus.PAID) {
            attempt.markSucceeded(providerTransactionId, responseCode, clock.instant());
            paymentAttemptRepository.save(attempt);
            return PaymentCallbackResult.ALREADY_CONFIRMED;
        }
        if (lockedOrder.getPayment() == null
                || lockedOrder.getPayment().getStatus() != PaymentStatus.PENDING
                || lockedOrder.getStatus() != OrderStatus.PENDING_PAYMENT) {
            attempt.markSucceeded(providerTransactionId, responseCode, clock.instant());
            paymentAttemptRepository.save(attempt);
            return PaymentCallbackResult.PROVIDER_SUCCESS_RECORDED;
        }

        PaymentCallbackResult result = orderService.processPaymentCallback(orderId, amount, true);
        Instant completedAt = clock.instant();
        attempt.markSucceeded(providerTransactionId, responseCode, completedAt);
        paymentAttemptRepository.save(attempt);
        return result;
    }

    private static ApplicationException notFound(String resourceType, Object resourceId) {
        return new ApplicationException(
                ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", resourceType, "resourceId", resourceId));
    }
}
