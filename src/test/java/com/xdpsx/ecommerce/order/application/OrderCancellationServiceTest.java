package com.xdpsx.ecommerce.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.order.api.dto.CancellationRequest;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderItem;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentAttempt;
import com.xdpsx.ecommerce.payment.domain.PaymentAttemptStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.xdpsx.ecommerce.refund.domain.Refund;
import com.xdpsx.ecommerce.refund.domain.RefundStatus;
import com.xdpsx.ecommerce.refund.persistence.RefundRepository;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private OrderCancellationService service;

    @BeforeEach
    void setUp() {
        service = new OrderCancellationService(
                orderRepository,
                userRepository,
                paymentAttemptRepository,
                refundRepository,
                inventoryBalanceRepository,
                orderMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                entityManager);
        lenient()
                .when(orderMapper.fromEntityToDetails(any()))
                .thenReturn(com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO.builder()
                        .build());
    }

    @Test
    void customerCancellationOfPendingPayment_ShouldReleaseReservationAndCancelPaymentState() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        Order order = order(OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING, 1, 1);
        PaymentAttempt attempt = PaymentAttempt.builder()
                .payment(order.getPayment())
                .providerReference("attempt")
                .status(PaymentAttemptStatus.PENDING)
                .build();
        when(userRepository.findByEmail("buyer@example.test")).thenReturn(Optional.of(user));
        when(orderRepository.findByIdAndUserIdForUpdateRoot(42L, 7L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));
        when(inventoryBalanceRepository.findAllByVariantIdsForUpdate(List.of(101L)))
                .thenAnswer(invocation -> List.of(lastBalance = balance(101L, 1, 1)));
        when(paymentAttemptRepository.findPendingByPaymentIdForUpdate(null)).thenReturn(List.of(attempt));

        service.cancelForCustomer("buyer@example.test", 42L, new CancellationRequest("Changed my mind"));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.CANCELLED, order.getPayment().getStatus());
        assertEquals(PaymentAttemptStatus.CANCELLED, attempt.getStatus());
        assertEquals(1, orderBalance().getOnHand());
        assertEquals(0, orderBalance().getReserved());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void customerCancellationOfConfirmedOrder_ShouldRestockAndCreateOneFullRefund() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        Order order = order(OrderStatus.CONFIRMED, PaymentStatus.PAID, 0, 0);
        when(userRepository.findByEmail("buyer@example.test")).thenReturn(Optional.of(user));
        when(orderRepository.findByIdAndUserIdForUpdateRoot(42L, 7L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));
        when(inventoryBalanceRepository.findAllByVariantIdsForUpdate(List.of(101L)))
                .thenAnswer(invocation -> List.of(lastBalance = balance(101L, 0, 0)));
        when(refundRepository.findByPaymentIdForUpdate(null)).thenReturn(Optional.empty());

        service.cancelForCustomer("buyer@example.test", 42L, new CancellationRequest("No longer needed"));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.PAID, order.getPayment().getStatus());
        assertEquals(1, orderBalance().getOnHand());
        Refund refund = order.getPayment().getRefund();
        assertEquals(RefundStatus.PENDING, refund.getStatus());
        assertEquals(order.getTotalAmount(), refund.getAmount());
        assertEquals("buyer@example.test", refund.getRequestedBy());
        verify(refundRepository).save(refund);
    }

    @Test
    void customerCancellationOfProcessingOrder_ShouldBeRejectedWithoutInventoryChange() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        Order order = order(OrderStatus.PROCESSING, PaymentStatus.PAID, 0, 0);
        when(userRepository.findByEmail("buyer@example.test")).thenReturn(Optional.of(user));
        when(orderRepository.findByIdAndUserIdForUpdateRoot(42L, 7L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.cancelForCustomer("buyer@example.test", 42L, new CancellationRequest("Too late")));

        assertEquals("MALFORMED_REQUEST", exception.getCode().name());
        verifyNoInventoryCall();
    }

    @Test
    void adminCancellationOfProcessingOrder_ShouldUseTheSamePaidCompensation() {
        Order order = order(OrderStatus.PROCESSING, PaymentStatus.PAID, 0, 0);
        when(orderRepository.findByIdForUpdateRoot(42L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        when(inventoryBalanceRepository.findAllByVariantIdsForUpdate(List.of(101L)))
                .thenAnswer(invocation -> List.of(lastBalance = balance(101L, 0, 0)));
        when(refundRepository.findByPaymentIdForUpdate(null)).thenReturn(Optional.empty());

        service.cancelAsAdmin(42L, new CancellationRequest("Fulfillment failed"), "admin@example.test");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(1, orderBalance().getOnHand());
        assertEquals("admin@example.test", order.getCancelledBy());
        assertEquals(RefundStatus.PENDING, order.getPayment().getRefund().getStatus());
    }

    @Test
    void repeatedCancellation_ShouldReturnExistingTerminalResultWithoutCompensation() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        Order order = order(OrderStatus.CANCELLED, PaymentStatus.PAID, 0, 0);
        when(userRepository.findByEmail("buyer@example.test")).thenReturn(Optional.of(user));
        when(orderRepository.findByIdAndUserIdForUpdateRoot(42L, 7L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndUserIdForUpdate(42L, 7L)).thenReturn(Optional.of(order));

        service.cancelForCustomer("buyer@example.test", 42L, new CancellationRequest("Repeat request"));

        verifyNoInventoryCall();
        verify(refundRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    private InventoryBalance lastBalance;

    private InventoryBalance orderBalance() {
        return lastBalance;
    }

    private void verifyNoInventoryCall() {
        verify(inventoryBalanceRepository, never()).findAllByVariantIdsForUpdate(any());
    }

    private Order order(OrderStatus status, PaymentStatus paymentStatus, long onHand, long reserved) {
        Order order = Order.builder()
                .id(42L)
                .status(status)
                .totalAmount(BigDecimal.TEN)
                .currency("VND")
                .build();
        order.getItems()
                .add(OrderItem.builder()
                        .order(order)
                        .variantId(101L)
                        .quantity(1)
                        .build());
        order.setPayment(Payment.builder().order(order).status(paymentStatus).build());
        return order;
    }

    private static InventoryBalance balance(Long variantId, long onHand, long reserved) {
        var balance = InventoryBalance.zero(com.xdpsx.ecommerce.catalog.product.domain.ProductVariant.builder()
                .id(variantId)
                .build());
        if (onHand > 0) balance.adjustOnHand(onHand);
        if (reserved > 0) balance.reserve(reserved);
        return balance;
    }
}
