package com.xdpsx.ecommerce.refund.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.refund.api.dto.RefundCompleteRequest;
import com.xdpsx.ecommerce.refund.api.dto.RefundFailRequest;
import com.xdpsx.ecommerce.refund.domain.Refund;
import com.xdpsx.ecommerce.refund.domain.RefundStatus;
import com.xdpsx.ecommerce.refund.persistence.RefundLockTarget;
import com.xdpsx.ecommerce.refund.persistence.RefundRepository;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundLockTarget refundLockTarget;

    @Mock
    private EntityManager entityManager;

    private RefundService service;
    private Refund refund;
    private Payment payment;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new RefundService(
                refundRepository, orderRepository, paymentRepository, Clock.fixed(NOW, ZoneOffset.UTC), entityManager);
        order = Order.builder()
                .id(42L)
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("100.00"))
                .build();
        payment =
                Payment.builder().id(5L).order(order).status(PaymentStatus.PAID).build();
        order.setPayment(payment);
        refund = Refund.builder()
                .id(9L)
                .payment(payment)
                .status(RefundStatus.PENDING)
                .amount(new BigDecimal("100.00"))
                .currency("VND")
                .reason("Customer cancellation")
                .requestedBy("customer@example.test")
                .requestedAt(NOW.atOffset(ZoneOffset.UTC).toLocalDateTime())
                .build();
        when(refundRepository.findLockTargetById(9L)).thenReturn(Optional.of(refundLockTarget));
        when(refundLockTarget.getPaymentId()).thenReturn(5L);
        when(refundLockTarget.getOrderId()).thenReturn(42L);
        when(orderRepository.findByIdForUpdateRoot(42L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(payment));
        when(refundRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(refund));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void complete_ShouldRecordOutcomeWithoutChangingOriginalPaymentOrAmount() {
        var response = service.complete(9L, new RefundCompleteRequest("  vnp-refund-1  "), "admin@example.test");

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.externalReference()).isEqualTo("vnp-refund-1");
        assertThat(response.amount()).isEqualByComparingTo("100.00");
        assertThat(response.currency()).isEqualTo("VND");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void failedRefund_ShouldBeRetryableAndSucceededRefundCannotUseDifferentEvidence() {
        service.fail(9L, new RefundFailRequest("Provider unavailable"), "admin@example.test");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);

        service.retry(9L);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getFailureReason()).isNull();

        service.complete(9L, new RefundCompleteRequest("vnp-refund-1"), "admin@example.test");
        assertThatThrownBy(() -> service.complete(9L, new RefundCompleteRequest("different"), "admin@example.test"))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception ->
                        ((ApplicationException) exception).getCode().name())
                .isEqualTo("MALFORMED_REQUEST");
    }
}
