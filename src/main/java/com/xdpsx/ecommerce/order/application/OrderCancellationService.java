package com.xdpsx.ecommerce.order.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalanceAdjustmentException;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.order.api.dto.CancellationRequest;
import com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentAttempt;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.xdpsx.ecommerce.refund.domain.Refund;
import com.xdpsx.ecommerce.refund.domain.RefundStatus;
import com.xdpsx.ecommerce.refund.persistence.RefundRepository;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCancellationService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RefundRepository refundRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final OrderMapper orderMapper;
    private final Clock clock;

    @Transactional
    public OrderDetailsDTO cancelForCustomer(String userEmail, Long orderId, CancellationRequest request) {
        User user = userRepository
                .findByEmail(EmailIdentity.canonicalize(userEmail))
                .orElseThrow(() -> notFound("user", userEmail));
        Order order = orderRepository
                .findByIdAndUserIdForUpdate(orderId, user.getId())
                .orElseThrow(() -> notFound("order", orderId));
        return cancel(order, request, EmailIdentity.canonicalize(userEmail), false);
    }

    @Transactional
    public OrderDetailsDTO cancelAsAdmin(Long orderId, CancellationRequest request, String actor) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> notFound("order", orderId));
        return cancel(order, request, normalizeActor(actor), true);
    }

    private OrderDetailsDTO cancel(Order order, CancellationRequest request, String actor, boolean admin) {
        String reason = normalizeReason(request);
        if (order.getStatus() == OrderStatus.CANCELLED) return mapDetails(order, admin);
        if (!isAllowed(order.getStatus(), admin)) {
            throw new ApplicationException(
                    ErrorCode.MALFORMED_REQUEST,
                    Map.of(
                            "reason",
                            "orderCancellationNotAllowed",
                            "status",
                            order.getStatus().name()));
        }

        Payment payment = order.getPayment();
        Refund refund = payment == null
                ? null
                : refundRepository.findByPaymentIdForUpdate(payment.getId()).orElse(null);
        boolean unpaid = order.getStatus() == OrderStatus.PENDING_PAYMENT;
        if (unpaid && (payment == null || payment.getStatus() != PaymentStatus.PENDING)) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "paymentStateMismatch"));
        }
        if (!unpaid && (payment == null || payment.getStatus() != PaymentStatus.PAID)) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "paymentStateMismatch"));
        }

        Map<Long, InventoryBalance> balances = lockBalances(order);
        try {
            order.getItems().forEach(item -> {
                InventoryBalance balance = balances.get(item.getVariantId());
                if (balance == null) throw new InventoryBalanceAdjustmentException("inventory balance is missing");
                if (unpaid) balance.release(item.getQuantity());
                else balance.restockConsumed(item.getQuantity());
            });
        } catch (RuntimeException exception) {
            throw new ApplicationException(
                    ErrorCode.INVENTORY_ADJUSTMENT_REJECTED, Map.of("orderId", order.getId()), exception);
        }

        var completedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (unpaid) {
            List<PaymentAttempt> attempts = paymentAttemptRepository.findPendingByPaymentIdForUpdate(payment.getId());
            attempts.forEach(attempt -> attempt.markCancelled(clock.instant()));
            payment.markCancelled();
        } else {
            if (refund == null) {
                refund = Refund.builder()
                        .payment(payment)
                        .status(RefundStatus.PENDING)
                        .amount(order.getTotalAmount())
                        .currency(order.getCurrency())
                        .reason(reason)
                        .requestedBy(actor)
                        .requestedAt(completedAt)
                        .build();
                refundRepository.save(refund);
                payment.setRefund(refund);
            }
        }
        order.cancel(reason, actor, completedAt);
        orderRepository.save(order);
        return mapDetails(order, admin);
    }

    private OrderDetailsDTO mapDetails(Order order, boolean admin) {
        return admin ? orderMapper.fromEntityToDetails(order) : orderMapper.fromEntityToCustomerDetails(order);
    }

    private Map<Long, InventoryBalance> lockBalances(Order order) {
        List<Long> variantIds = order.getItems().stream()
                .map(item -> item.getVariantId())
                .distinct()
                .sorted()
                .toList();
        if (variantIds.isEmpty()) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "orderHasNoItems"));
        }
        Map<Long, InventoryBalance> balances =
                inventoryBalanceRepository.findAllByVariantIdsForUpdate(variantIds).stream()
                        .collect(Collectors.toMap(InventoryBalance::getVariantId, value -> value));
        if (balances.size() != Set.copyOf(variantIds).size()) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "missingInventoryBalance"));
        }
        return new HashMap<>(balances);
    }

    private static boolean isAllowed(OrderStatus status, boolean admin) {
        return status == OrderStatus.PENDING_PAYMENT
                || status == OrderStatus.CONFIRMED
                || (admin && status == OrderStatus.PROCESSING);
    }

    private static String normalizeReason(CancellationRequest request) {
        if (request == null || request.reason() == null) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "reason"));
        }
        String reason = request.reason().trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "reason"));
        }
        return reason;
    }

    private static String normalizeActor(String actor) {
        String normalized = EmailIdentity.canonicalize(actor);
        if (normalized == null || normalized.isBlank() || normalized.length() > 320) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "actor"));
        }
        return normalized;
    }

    private static ApplicationException notFound(String resourceType, Object resourceId) {
        return new ApplicationException(
                ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", resourceType, "resourceId", resourceId));
    }
}
