package com.xdpsx.ecommerce.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.config.PaymentAttemptProperties;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentAttempt;
import com.xdpsx.ecommerce.payment.domain.PaymentAttemptStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class PaymentAttemptPreparationServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    private PaymentAttemptPreparationService service;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        PaymentAttemptProperties properties = new PaymentAttemptProperties();
        properties.setLifetime(Duration.ofMinutes(15));
        service = new PaymentAttemptPreparationService(
                userRepository,
                orderRepository,
                paymentAttemptRepository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        User user = User.builder().id(7L).email("buyer@example.test").build();
        payment = Payment.builder().id(5L).status(PaymentStatus.PENDING).build();
        order = Order.builder()
                .id(42L)
                .user(user)
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(new BigDecimal("125.50"))
                .currency("VND")
                .reservationExpiresAt(NOW.plus(Duration.ofMinutes(10)))
                .build();
        order.setPayment(payment);
        when(userRepository.findByEmail("buyer@example.test")).thenReturn(Optional.of(user));
        when(orderRepository.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));
    }

    @Test
    void prepare_ShouldCreateAttemptFromOrderSnapshotAndCapExpiryAtReservation() {
        when(paymentAttemptRepository.findTopByPaymentIdAndStatusOrderByCreatedAtDesc(5L, PaymentAttemptStatus.PENDING))
                .thenReturn(Optional.empty());

        PreparedPaymentAttempt prepared = service.prepare(" buyer@example.test ", 42L);

        assertThat(prepared.orderId()).isEqualTo(42L);
        assertThat(prepared.userId()).isEqualTo(7L);
        assertThat(prepared.expectedAmount()).isEqualByComparingTo("125.50");
        assertThat(prepared.currency()).isEqualTo("VND");
        assertThat(prepared.providerReference()).hasSizeLessThanOrEqualTo(100);
        assertThat(prepared.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        verify(paymentAttemptRepository).save(any(PaymentAttempt.class));
    }

    @Test
    void prepare_ShouldReuseUnexpiredPendingAttempt() {
        PaymentAttempt active = PaymentAttempt.builder()
                .payment(payment)
                .providerReference("attempt-1")
                .status(PaymentAttemptStatus.PENDING)
                .expectedAmount(new BigDecimal("125.50"))
                .currency("VND")
                .createdAt(NOW.minusSeconds(10))
                .expiresAt(NOW.plus(Duration.ofMinutes(5)))
                .build();
        when(paymentAttemptRepository.findTopByPaymentIdAndStatusOrderByCreatedAtDesc(5L, PaymentAttemptStatus.PENDING))
                .thenReturn(Optional.of(active));

        PreparedPaymentAttempt prepared = service.prepare("buyer@example.test", 42L);

        assertThat(prepared.providerReference()).isEqualTo("attempt-1");
        verify(paymentAttemptRepository, never()).save(any());
    }

    @Test
    void prepare_ShouldExpirePreviousAttemptBeforeCreatingNewOne() {
        PaymentAttempt expired = PaymentAttempt.builder()
                .payment(payment)
                .providerReference("expired-attempt")
                .status(PaymentAttemptStatus.PENDING)
                .expectedAmount(new BigDecimal("125.50"))
                .currency("VND")
                .createdAt(NOW.minusSeconds(600))
                .expiresAt(NOW)
                .build();
        when(paymentAttemptRepository.findTopByPaymentIdAndStatusOrderByCreatedAtDesc(5L, PaymentAttemptStatus.PENDING))
                .thenReturn(Optional.of(expired));

        PreparedPaymentAttempt prepared = service.prepare("buyer@example.test", 42L);

        assertThat(expired.getStatus()).isEqualTo(PaymentAttemptStatus.EXPIRED);
        assertThat(prepared.providerReference()).isNotEqualTo("expired-attempt");
        verify(paymentAttemptRepository).save(any(PaymentAttempt.class));
    }

    @Test
    void prepare_ShouldRejectForeignOrIneligibleOrder() {
        when(orderRepository.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare("buyer@example.test", 42L)).isInstanceOf(ApplicationException.class);
        verify(paymentAttemptRepository, never()).save(any());
    }

    @Test
    void prepare_ShouldRejectReservationThatHasExpired() {
        order.setReservationExpiresAt(NOW);

        assertThatThrownBy(() -> service.prepare("buyer@example.test", 42L))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getCode())
                .isEqualTo(com.xdpsx.ecommerce.common.error.ErrorCode.MALFORMED_REQUEST);
        verify(paymentAttemptRepository, never()).save(any());
    }

    @Test
    void prepare_ShouldRejectConfirmedOrder() {
        Order confirmed = Order.builder()
                .id(42L)
                .user(order.getUser())
                .status(OrderStatus.CONFIRMED)
                .totalAmount(order.getTotalAmount())
                .currency("VND")
                .reservationExpiresAt(NOW.plus(Duration.ofMinutes(10)))
                .build();
        confirmed.setPayment(payment);
        when(orderRepository.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.prepare("buyer@example.test", 42L)).isInstanceOf(ApplicationException.class);
        verify(paymentAttemptRepository, never()).save(any());
    }
}
