package com.xdpsx.ecommerce.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.order.api.dto.OrderStatusUpdate;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderItem;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.domain.ShippingAddressSnapshot;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentAttempt;
import com.xdpsx.ecommerce.payment.domain.PaymentAttemptStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {
    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void getAllOrders_ShouldUseStableCreatedAtAndIdSort() {
        when(orderRepository.findAll(
                        org.mockito.ArgumentMatchers.<Specification<Order>>any(),
                        org.mockito.ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));

        orderService.getAllOrders(1, 5, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Order>>any(), pageable.capture());
        assertEquals(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
                pageable.getValue().getSort());
    }

    @Test
    void expirePendingOrder_ShouldReleaseReservationAndMarkOrderExpired() {
        Order order = order(BigDecimal.TEN, PaymentStatus.PENDING);
        order.setReservationExpiresAt(Instant.parse("2026-01-01T00:00:00Z"));
        InventoryBalance balance = balance(1);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));
        when(inventoryBalanceRepository.findAllByVariantIdsForUpdate(List.of(101L)))
                .thenReturn(List.of(balance));
        PaymentAttempt attempt = PaymentAttempt.builder()
                .payment(order.getPayment())
                .providerReference("attempt")
                .status(PaymentAttemptStatus.PENDING)
                .expectedAmount(BigDecimal.TEN)
                .currency("VND")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T00:15:00Z"))
                .build();
        when(paymentAttemptRepository.findPendingByPaymentIdForUpdate(null)).thenReturn(List.of(attempt));

        assertEquals(true, orderService.expirePendingOrder(42L, Instant.parse("2026-01-01T00:00:01Z")));

        assertEquals(OrderStatus.PAYMENT_EXPIRED, order.getStatus());
        assertEquals(PaymentStatus.EXPIRED, order.getPayment().getStatus());
        assertEquals(PaymentAttemptStatus.EXPIRED, attempt.getStatus());
        assertEquals(0, balance.getReserved());
        assertEquals(1, balance.getOnHand());
    }

    @Test
    void expirePendingOrder_ShouldRecheckStateBeforeTouchingInventory() {
        Order order = order(BigDecimal.TEN, PaymentStatus.PENDING, OrderStatus.CONFIRMED);
        order.setReservationExpiresAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));

        assertEquals(false, orderService.expirePendingOrder(42L, Instant.parse("2026-01-01T00:00:01Z")));

        verify(inventoryBalanceRepository, never()).findAllByVariantIdsForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateOrderStatus_ShouldUseForwardDomainTransitionAndShippingSnapshot() {
        Order order = Order.builder()
                .id(42L)
                .status(OrderStatus.CONFIRMED)
                .address("legacy address")
                .mobileNumber("legacy phone")
                .currency("VND")
                .shippingAddress(new ShippingAddressSnapshot(
                        "Snapshot buyer", "+84901234567", "Snapshot street", "Ward", "District", "City", null))
                .build();
        order.setPayment(
                Payment.builder().order(order).status(PaymentStatus.PENDING).build());
        OrderStatusUpdate request = new OrderStatusUpdate();
        request.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        var response = orderService.updateOrderStatus(42L, request);

        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        assertEquals("PROCESSING", response.getStatus());
        assertEquals("Snapshot street", response.getAddressLine());
        assertEquals("Snapshot buyer", response.getRecipientName());
        verify(orderRepository).save(order);
        verify(orderRepository).findByIdForUpdate(42L);
    }

    @Test
    void updateOrderStatus_ShouldRejectNonForwardTransitionWithoutSaving() {
        Order order = order(BigDecimal.TEN, PaymentStatus.PENDING);
        OrderStatusUpdate request = new OrderStatusUpdate();
        request.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> orderService.updateOrderStatus(42L, request));

        assertEquals(com.xdpsx.ecommerce.common.error.ErrorCode.MALFORMED_REQUEST, exception.getCode());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        verify(orderRepository, never()).save(order);
    }

    @Test
    void updateOrderStatus_ShouldAllowFullFulfillmentChainAndSetDeliveredAtOnlyAtEnd() {
        Order order = order(BigDecimal.TEN, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        updateStatus(OrderStatus.PROCESSING);
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        assertNull(order.getDeliveredAt());

        updateStatus(OrderStatus.SHIPPED);
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertNull(order.getDeliveredAt());

        updateStatus(OrderStatus.DELIVERED);
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertNotNull(order.getDeliveredAt());
        verify(orderRepository, org.mockito.Mockito.times(3)).findByIdForUpdate(42L);
        verify(orderRepository, org.mockito.Mockito.times(3)).save(order);
    }

    private void updateStatus(OrderStatus status) {
        OrderStatusUpdate request = new OrderStatusUpdate();
        request.setStatus(status);
        orderService.updateOrderStatus(42L, request);
    }

    private static Order order(BigDecimal amount, PaymentStatus status) {
        return order(amount, status, OrderStatus.PENDING_PAYMENT);
    }

    private static Order order(BigDecimal amount, PaymentStatus status, OrderStatus orderStatus) {
        Order order =
                Order.builder().id(42L).totalAmount(amount).status(orderStatus).build();
        order.getItems()
                .add(OrderItem.builder()
                        .order(order)
                        .variantId(101L)
                        .quantity(1)
                        .build());
        order.setPayment(Payment.builder().order(order).status(status).build());
        return order;
    }

    private static InventoryBalance balance(long reserved) {
        InventoryBalance balance =
                InventoryBalance.zero(ProductVariant.builder().id(101L).build());
        balance.adjustOnHand(reserved);
        balance.reserve(reserved);
        return balance;
    }
}
