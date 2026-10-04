package com.xdpsx.ecommerce.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.order.application.PaymentCallbackResult;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentAttempt;
import com.xdpsx.ecommerce.payment.domain.PaymentAttemptStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.refund.domain.Refund;
import com.xdpsx.ecommerce.refund.domain.RefundStatus;
import com.xdpsx.ecommerce.refund.persistence.RefundRepository;

@ExtendWith(MockitoExtension.class)
class PaymentAttemptCallbackServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Mock
    private RefundRepository refundRepository;

    private PaymentAttemptCallbackService service;
    private PaymentAttempt attempt;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new PaymentAttemptCallbackService(
                paymentAttemptRepository,
                orderRepository,
                paymentRepository,
                inventoryBalanceRepository,
                refundRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        order = Order.builder()
                .id(42L)
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(new BigDecimal("100.00"))
                .build();
        payment = Payment.builder()
                .id(5L)
                .order(order)
                .status(PaymentStatus.PENDING)
                .build();
        order.setPayment(payment);
        order.getItems()
                .add(com.xdpsx.ecommerce.order.domain.OrderItem.builder()
                        .order(order)
                        .variantId(101L)
                        .quantity(1)
                        .build());
        attempt = PaymentAttempt.builder()
                .id(7L)
                .payment(payment)
                .providerReference("attempt-uuid")
                .status(PaymentAttemptStatus.PENDING)
                .expectedAmount(new BigDecimal("100.00"))
                .currency("VND")
                .createdAt(NOW)
                .expiresAt(NOW.plusSeconds(600))
                .build();
        when(paymentAttemptRepository.findByProviderReferenceWithPaymentAndOrder("attempt-uuid"))
                .thenReturn(Optional.of(attempt));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findByProviderReferenceForUpdate("attempt-uuid"))
                .thenReturn(Optional.of(attempt));
    }

    @Test
    void process_ShouldMarkExactAttemptFailedWithoutChangingOrder() {
        PaymentCallbackResult result = service.process("attempt-uuid", new BigDecimal("100.00"), false, null, "24");

        assertThat(result).isEqualTo(PaymentCallbackResult.CONFIRMED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(attempt.getResponseCode()).isEqualTo("24");
        verify(inventoryBalanceRepository, org.mockito.Mockito.never())
                .findAllByVariantIdsForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void process_ShouldConfirmOrderAndPersistExactAttemptOnSuccess() {
        stubSuccessfulCallback();
        PaymentAttempt otherAttempt = PaymentAttempt.builder()
                .id(8L)
                .payment(payment)
                .providerReference("other-attempt")
                .status(PaymentAttemptStatus.PENDING)
                .expectedAmount(new BigDecimal("100.00"))
                .currency("VND")
                .createdAt(NOW)
                .expiresAt(NOW.plusSeconds(600))
                .build();
        when(paymentAttemptRepository.findPendingByPaymentIdForUpdate(5L)).thenReturn(List.of(otherAttempt));
        PaymentCallbackResult result =
                service.process("attempt-uuid", new BigDecimal("100.00"), true, "provider-transaction", "00");

        assertThat(result).isEqualTo(PaymentCallbackResult.CONFIRMED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getProviderTransactionId()).isEqualTo("provider-transaction");
        assertThat(attempt.getCompletedAt()).isEqualTo(NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(otherAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.EXPIRED);
        verify(paymentRepository).save(payment);
        verify(paymentAttemptRepository).save(attempt);
    }

    @Test
    void process_ShouldRollbackWhenInventoryBalanceIsMissing() {
        when(inventoryBalanceRepository.findAllByVariantIdsForUpdate(List.of(101L)))
                .thenReturn(List.of());

        assertThatThrownBy(() ->
                        service.process("attempt-uuid", new BigDecimal("100.00"), true, "provider-transaction", "00"))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getCode())
                .isEqualTo(com.xdpsx.ecommerce.common.error.ErrorCode.INTERNAL_ERROR);

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PENDING);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void process_ShouldReconcileSuccessAfterFailureWhileOrderRemainsPending() {
        stubSuccessfulCallback();
        service.process("attempt-uuid", new BigDecimal("100.00"), false, null, "24");
        PaymentCallbackResult result =
                service.process("attempt-uuid", new BigDecimal("100.00"), true, "late-provider-transaction", "00");

        assertThat(result).isEqualTo(PaymentCallbackResult.CONFIRMED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getProviderTransactionId()).isEqualTo("late-provider-transaction");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void process_ShouldAcknowledgeDuplicateFailureWithoutChangingAttempt() {
        service.process("attempt-uuid", new BigDecimal("100.00"), false, null, "24");

        PaymentCallbackResult result = service.process("attempt-uuid", new BigDecimal("100.00"), false, null, "24");

        assertThat(result).isEqualTo(PaymentCallbackResult.CONFIRMED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(attempt.getResponseCode()).isEqualTo("24");
        verify(inventoryBalanceRepository, org.mockito.Mockito.never())
                .findAllByVariantIdsForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void process_ShouldReconcileSuccessAfterExpiryWhileOrderRemainsPending() {
        stubSuccessfulCallback();
        attempt.markExpired(NOW);
        PaymentCallbackResult result =
                service.process("attempt-uuid", new BigDecimal("100.00"), true, "late-provider-transaction", "00");

        assertThat(result).isEqualTo(PaymentCallbackResult.CONFIRMED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getProviderTransactionId()).isEqualTo("late-provider-transaction");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void process_ShouldRecordSuccessAfterExpiryWithoutReopeningTerminalOrder() {
        attempt.markExpired(NOW);
        order = Order.builder()
                .id(42L)
                .status(OrderStatus.PAYMENT_EXPIRED)
                .totalAmount(new BigDecimal("100.00"))
                .build();
        Payment payment = Payment.builder()
                .id(5L)
                .order(order)
                .status(PaymentStatus.EXPIRED)
                .build();
        order.setPayment(payment);
        attempt = PaymentAttempt.builder()
                .id(7L)
                .payment(payment)
                .providerReference("attempt-uuid")
                .status(PaymentAttemptStatus.EXPIRED)
                .expectedAmount(new BigDecimal("100.00"))
                .currency("VND")
                .createdAt(NOW)
                .expiresAt(NOW)
                .completedAt(NOW)
                .build();
        when(paymentAttemptRepository.findByProviderReferenceWithPaymentAndOrder("attempt-uuid"))
                .thenReturn(Optional.of(attempt));
        when(paymentAttemptRepository.findByProviderReferenceForUpdate("attempt-uuid"))
                .thenReturn(Optional.of(attempt));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));

        PaymentCallbackResult result =
                service.process("attempt-uuid", new BigDecimal("100.00"), true, "late-provider-transaction", "00");

        assertThat(result).isEqualTo(PaymentCallbackResult.PROVIDER_SUCCESS_RECORDED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getProviderTransactionId()).isEqualTo("late-provider-transaction");
        verify(inventoryBalanceRepository, org.mockito.Mockito.never())
                .findAllByVariantIdsForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void process_ShouldCreatePendingRefundAfterCancellationWithoutReopeningOrderOrConsumingInventory() {
        order.cancel("customer request", "buyer@example.test", java.time.LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        payment.markCancelled();
        attempt.markCancelled(NOW);
        when(refundRepository.findByPaymentIdForUpdate(5L)).thenReturn(Optional.empty());

        PaymentCallbackResult result =
                service.process("attempt-uuid", new BigDecimal("100.00"), true, "late-provider-transaction", "00");

        assertThat(result).isEqualTo(PaymentCallbackResult.PROVIDER_SUCCESS_RECORDED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getRefund()).isNotNull();
        assertThat(payment.getRefund().getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(payment.getRefund().getAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.getRefund().getRequestedBy()).isEqualTo("payment-callback");
        verify(refundRepository).save(org.mockito.ArgumentMatchers.any(Refund.class));
        verify(inventoryBalanceRepository, org.mockito.Mockito.never())
                .findAllByVariantIdsForUpdate(org.mockito.ArgumentMatchers.any());
    }

    private static InventoryBalance balance(long reserved) {
        InventoryBalance balance =
                InventoryBalance.zero(ProductVariant.builder().id(101L).build());
        balance.adjustOnHand(reserved);
        balance.reserve(reserved);
        return balance;
    }

    private void stubSuccessfulCallback() {
        when(inventoryBalanceRepository.findAllByVariantIdsForUpdate(List.of(101L)))
                .thenReturn(List.of(balance(1)));
        when(paymentAttemptRepository.findPendingByPaymentIdForUpdate(5L)).thenReturn(List.of());
    }
}
