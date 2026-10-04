package com.xdpsx.ecommerce.refund.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.refund.api.dto.RefundCompleteRequest;
import com.xdpsx.ecommerce.refund.api.dto.RefundFailRequest;
import com.xdpsx.ecommerce.refund.api.dto.RefundResponse;
import com.xdpsx.ecommerce.refund.domain.Refund;
import com.xdpsx.ecommerce.refund.persistence.RefundLockTarget;
import com.xdpsx.ecommerce.refund.persistence.RefundRepository;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefundService {
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public RefundResponse get(Long refundId) {
        return toResponse(refundRepository.findByIdWithPaymentAndOrder(refundId).orElseThrow(() -> notFound(refundId)));
    }

    @Transactional
    public RefundResponse complete(Long refundId, RefundCompleteRequest request, String actor) {
        Refund refund = lockRefundGraph(refundId);
        String externalReference = normalizeExternalReference(request);
        try {
            refund.complete(normalizeActor(actor), now(), externalReference);
        } catch (IllegalStateException exception) {
            throw transitionRejected(refund, exception);
        }
        return toResponse(refundRepository.save(refund));
    }

    @Transactional
    public RefundResponse fail(Long refundId, RefundFailRequest request, String actor) {
        Refund refund = lockRefundGraph(refundId);
        String failureReason = normalizeFailureReason(request);
        try {
            refund.fail(normalizeActor(actor), now(), failureReason);
        } catch (IllegalStateException exception) {
            throw transitionRejected(refund, exception);
        }
        return toResponse(refundRepository.save(refund));
    }

    @Transactional
    public RefundResponse retry(Long refundId) {
        Refund refund = lockRefundGraph(refundId);
        try {
            refund.retry();
        } catch (IllegalStateException exception) {
            throw transitionRejected(refund, exception);
        }
        return toResponse(refundRepository.save(refund));
    }

    private Refund lockRefundGraph(Long refundId) {
        RefundLockTarget target = refundRepository.findLockTargetById(refundId).orElseThrow(() -> notFound(refundId));
        Order orderLock =
                orderRepository.findByIdForUpdateRoot(target.getOrderId()).orElseThrow(() -> notFound(refundId));
        entityManager.refresh(orderLock, LockModeType.PESSIMISTIC_WRITE);
        entityManager.clear();
        paymentRepository.findByIdForUpdate(target.getPaymentId()).orElseThrow(() -> notFound(refundId));
        entityManager.clear();
        return refundRepository.findByIdForUpdate(refundId).orElseThrow(() -> notFound(refundId));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static RefundResponse toResponse(Refund refund) {
        Payment payment = refund.getPayment();
        return RefundResponse.builder()
                .id(refund.getId())
                .paymentId(payment == null ? null : payment.getId())
                .orderId(
                        payment == null || payment.getOrder() == null
                                ? null
                                : payment.getOrder().getId())
                .status(refund.getStatus().name())
                .amount(refund.getAmount())
                .currency(refund.getCurrency())
                .reason(refund.getReason())
                .requestedBy(refund.getRequestedBy())
                .requestedAt(refund.getRequestedAt())
                .processedBy(refund.getProcessedBy())
                .completedAt(refund.getCompletedAt())
                .externalReference(refund.getExternalReference())
                .failureReason(refund.getFailureReason())
                .build();
    }

    private static String normalizeExternalReference(RefundCompleteRequest request) {
        if (request == null || request.externalReference() == null) return null;
        String value = request.externalReference().trim();
        if (value.isEmpty()) return null;
        if (value.length() > 100) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "externalReference"));
        }
        return value;
    }

    private static String normalizeFailureReason(RefundFailRequest request) {
        if (request == null || request.failureReason() == null) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "failureReason"));
        }
        String value = request.failureReason().trim();
        if (value.isEmpty() || value.length() > 500) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "failureReason"));
        }
        return value;
    }

    private static String normalizeActor(String actor) {
        String value = EmailIdentity.canonicalize(actor);
        if (value == null || value.isBlank() || value.length() > 320) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "actor"));
        }
        return value;
    }

    private static ApplicationException notFound(Long refundId) {
        return new ApplicationException(
                ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "refund", "resourceId", refundId));
    }

    private static ApplicationException transitionRejected(Refund refund, IllegalStateException cause) {
        return new ApplicationException(
                ErrorCode.MALFORMED_REQUEST,
                Map.of(
                        "reason",
                        "invalidRefundTransition",
                        "status",
                        refund.getStatus().name()),
                cause);
    }
}
