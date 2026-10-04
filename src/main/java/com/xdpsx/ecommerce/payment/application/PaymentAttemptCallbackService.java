package com.xdpsx.ecommerce.payment.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.order.application.PaymentCallbackResult;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentAttempt;
import com.xdpsx.ecommerce.payment.domain.PaymentAttemptStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentMethod;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.refund.domain.Refund;
import com.xdpsx.ecommerce.refund.domain.RefundStatus;
import com.xdpsx.ecommerce.refund.persistence.RefundRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentAttemptCallbackService {
    private static final String LATE_PAYMENT_ACTOR = "payment-callback";
    private static final String LATE_PAYMENT_REASON = "Payment received after order cancellation";

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final RefundRepository refundRepository;
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
                || lockedOrder.getTotalAmount() == null
                || lockedOrder.getTotalAmount().compareTo(amount) != 0
                || !"VND".equals(attempt.getCurrency())) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "amountMismatch"));
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
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "invalidTransaction"));
        }

        Payment payment = lockedOrder.getPayment();
        if (payment == null
                || attempt.getPayment() == null
                || !payment.getId().equals(attempt.getPayment().getId())
                || payment.getOrder() == null
                || !orderId.equals(payment.getOrder().getId())) {
            throw new ApplicationException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            attempt.markSucceeded(providerTransactionId, responseCode, clock.instant());
            paymentAttemptRepository.save(attempt);
            return PaymentCallbackResult.ALREADY_CONFIRMED;
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED || lockedOrder.getStatus() == OrderStatus.CANCELLED) {
            if (payment.getStatus() != PaymentStatus.CANCELLED || lockedOrder.getStatus() != OrderStatus.CANCELLED) {
                throw new ApplicationException(ErrorCode.CONCURRENT_MODIFICATION);
            }
            Instant completedAt = clock.instant();
            LocalDateTime paidAt = LocalDateTime.ofInstant(completedAt, ZoneOffset.UTC);
            payment.markPaidAfterCancellation(PaymentMethod.VNPAY, paidAt);
            attempt.markSucceeded(providerTransactionId, responseCode, completedAt);
            createLatePaymentRefund(payment, lockedOrder, paidAt);
            paymentRepository.save(payment);
            paymentAttemptRepository.save(attempt);
            return PaymentCallbackResult.PROVIDER_SUCCESS_RECORDED;
        }
        if (payment.getStatus() != PaymentStatus.PENDING || lockedOrder.getStatus() != OrderStatus.PENDING_PAYMENT) {
            attempt.markSucceeded(providerTransactionId, responseCode, clock.instant());
            paymentAttemptRepository.save(attempt);
            return PaymentCallbackResult.PROVIDER_SUCCESS_RECORDED;
        }

        List<Long> variantIds = lockedOrder.getItems().stream()
                .map(item -> item.getVariantId())
                .distinct()
                .sorted()
                .toList();
        if (variantIds.isEmpty()) {
            throw new ApplicationException(ErrorCode.INTERNAL_ERROR, Map.of("reason", "orderHasNoItems"));
        }
        Map<Long, InventoryBalance> balances =
                inventoryBalanceRepository.findAllByVariantIdsForUpdate(variantIds).stream()
                        .collect(Collectors.toMap(InventoryBalance::getVariantId, value -> value));
        if (balances.size() != Set.copyOf(variantIds).size()) {
            throw new ApplicationException(ErrorCode.INTERNAL_ERROR, Map.of("reason", "missingInventoryBalance"));
        }
        try {
            lockedOrder.getItems().forEach(item -> balances.get(item.getVariantId())
                    .consumeReserved(item.getQuantity()));
        } catch (RuntimeException exception) {
            throw new ApplicationException(
                    ErrorCode.INTERNAL_ERROR, Map.of("reason", "reservationUnavailable"), exception);
        }

        Instant completedAt = clock.instant();
        payment.markPaid(PaymentMethod.VNPAY, LocalDateTime.ofInstant(completedAt, ZoneOffset.UTC));
        lockedOrder.confirmPayment();
        paymentRepository.save(payment);
        attempt.markSucceeded(providerTransactionId, responseCode, completedAt);
        paymentAttemptRepository.save(attempt);
        expireOtherPendingAttempts(payment.getId(), attempt.getId(), completedAt);
        return PaymentCallbackResult.CONFIRMED;
    }

    private void createLatePaymentRefund(Payment payment, Order order, LocalDateTime requestedAt) {
        if (refundRepository.findByPaymentIdForUpdate(payment.getId()).isPresent()) return;
        Refund refund = Refund.builder()
                .payment(payment)
                .status(RefundStatus.PENDING)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .reason(LATE_PAYMENT_REASON)
                .requestedBy(LATE_PAYMENT_ACTOR)
                .requestedAt(requestedAt)
                .build();
        refundRepository.save(refund);
        payment.setRefund(refund);
    }

    private void expireOtherPendingAttempts(Long paymentId, Long succeededAttemptId, Instant completedAt) {
        paymentAttemptRepository.findPendingByPaymentIdForUpdate(paymentId).stream()
                .filter(candidate -> !candidate.getId().equals(succeededAttemptId))
                .forEach(candidate -> candidate.markExpired(completedAt));
    }

    private static ApplicationException notFound(String resourceType, Object resourceId) {
        return new ApplicationException(
                ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", resourceType, "resourceId", resourceId));
    }
}
