package com.xdpsx.ecommerce.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.order.application.PaymentCallbackResult;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentAttempt;
import com.xdpsx.ecommerce.payment.domain.PaymentAttemptStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;

@ExtendWith(MockitoExtension.class)
class PaymentAttemptCallbackServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    private PaymentAttemptCallbackService service;
    private PaymentAttempt attempt;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new PaymentAttemptCallbackService(
                paymentAttemptRepository, orderRepository, orderService, Clock.fixed(NOW, ZoneOffset.UTC));
        order = Order.builder().id(42L).status(OrderStatus.PENDING_PAYMENT).build();
        Payment payment = Payment.builder()
                .id(5L)
                .order(order)
                .status(PaymentStatus.PENDING)
                .build();
        order.setPayment(payment);
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
        verify(orderService, org.mockito.Mockito.never())
                .processPaymentCallback(
                        org.mockito.ArgumentMatchers.anyLong(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void process_ShouldConfirmOrderAndPersistExactAttemptOnSuccess() {
        when(orderService.processPaymentCallback(42L, new BigDecimal("100.00"), true))
                .thenReturn(PaymentCallbackResult.CONFIRMED);

        PaymentCallbackResult result =
                service.process("attempt-uuid", new BigDecimal("100.00"), true, "provider-transaction", "00");

        assertThat(result).isEqualTo(PaymentCallbackResult.CONFIRMED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getProviderTransactionId()).isEqualTo("provider-transaction");
        assertThat(attempt.getCompletedAt()).isEqualTo(NOW);
        verify(orderService).processPaymentCallback(42L, new BigDecimal("100.00"), true);
        verify(paymentAttemptRepository).save(attempt);
    }

    @Test
    void process_ShouldReconcileSuccessAfterFailureWhileOrderRemainsPending() {
        service.process("attempt-uuid", new BigDecimal("100.00"), false, null, "24");
        when(orderService.processPaymentCallback(42L, new BigDecimal("100.00"), true))
                .thenReturn(PaymentCallbackResult.CONFIRMED);

        PaymentCallbackResult result =
                service.process("attempt-uuid", new BigDecimal("100.00"), true, "late-provider-transaction", "00");

        assertThat(result).isEqualTo(PaymentCallbackResult.CONFIRMED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getProviderTransactionId()).isEqualTo("late-provider-transaction");
        verify(orderService).processPaymentCallback(42L, new BigDecimal("100.00"), true);
    }

    @Test
    void process_ShouldAcknowledgeDuplicateFailureWithoutChangingAttempt() {
        service.process("attempt-uuid", new BigDecimal("100.00"), false, null, "24");

        PaymentCallbackResult result = service.process("attempt-uuid", new BigDecimal("100.00"), false, null, "24");

        assertThat(result).isEqualTo(PaymentCallbackResult.CONFIRMED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(attempt.getResponseCode()).isEqualTo("24");
        verify(orderService, org.mockito.Mockito.never())
                .processPaymentCallback(
                        org.mockito.ArgumentMatchers.anyLong(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void process_ShouldReconcileSuccessAfterExpiryWhileOrderRemainsPending() {
        attempt.markExpired(NOW);
        when(orderService.processPaymentCallback(42L, new BigDecimal("100.00"), true))
                .thenReturn(PaymentCallbackResult.CONFIRMED);

        PaymentCallbackResult result =
                service.process("attempt-uuid", new BigDecimal("100.00"), true, "late-provider-transaction", "00");

        assertThat(result).isEqualTo(PaymentCallbackResult.CONFIRMED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getProviderTransactionId()).isEqualTo("late-provider-transaction");
        verify(orderService).processPaymentCallback(42L, new BigDecimal("100.00"), true);
    }

    @Test
    void process_ShouldRecordSuccessAfterExpiryWithoutReopeningTerminalOrder() {
        attempt.markExpired(NOW);
        order = Order.builder().id(42L).status(OrderStatus.PAYMENT_EXPIRED).build();
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
        verify(orderService, org.mockito.Mockito.never())
                .processPaymentCallback(
                        org.mockito.ArgumentMatchers.anyLong(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
