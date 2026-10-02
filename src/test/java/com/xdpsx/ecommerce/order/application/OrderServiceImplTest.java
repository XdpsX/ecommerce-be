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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.xdpsx.ecommerce.payment.domain.PaymentMethod;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
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

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void processPaymentCallback_ShouldMarkUnpaidOrderPaidAfterAmountAndStatusValidation() {
        Order order = order(BigDecimal.TEN, PaymentStatus.UNPAID);
        InventoryBalance balance = balance(1);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));
        when(inventoryBalanceRepository.findAllByVariantIdsForUpdate(List.of(101L)))
                .thenReturn(List.of(balance));

        orderService.processPaymentCallback(42L, new BigDecimal("10.00"), true);

        assertEquals(PaymentStatus.PAID, order.getPayment().getStatus());
        assertEquals(PaymentMethod.VNPAY, order.getPayment().getPaymentMethod());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(0, balance.getReserved());
        assertEquals(0, balance.getOnHand());
        verify(paymentRepository).save(order.getPayment());
    }

    @Test
    void processPaymentCallback_ShouldBeIdempotentForAlreadyPaidOrder() {
        Order order = order(BigDecimal.TEN, PaymentStatus.PAID);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));

        orderService.processPaymentCallback(42L, BigDecimal.TEN, true);

        verify(paymentRepository, never()).save(order.getPayment());
        verify(inventoryBalanceRepository, never()).findAllByVariantIdsForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void processPaymentCallback_ShouldRejectAmountMismatchWithoutChangingPayment() {
        Order order = order(BigDecimal.TEN, PaymentStatus.UNPAID);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));

        assertThrows(
                ApplicationException.class,
                () -> orderService.processPaymentCallback(42L, new BigDecimal("11.00"), true));

        assertEquals(PaymentStatus.UNPAID, order.getPayment().getStatus());
        verify(paymentRepository, never()).save(order.getPayment());
    }

    @Test
    void processPaymentCallback_ShouldLeavePendingOrderUnchangedForSignedFailure() {
        Order order = order(BigDecimal.TEN, PaymentStatus.UNPAID);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));

        orderService.processPaymentCallback(42L, BigDecimal.TEN, false);

        assertEquals(PaymentStatus.UNPAID, order.getPayment().getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        verify(inventoryBalanceRepository, never()).findAllByVariantIdsForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void processPaymentCallback_ShouldRejectWhenReservationIsMissing() {
        Order order = order(BigDecimal.TEN, PaymentStatus.UNPAID);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));
        when(inventoryBalanceRepository.findAllByVariantIdsForUpdate(List.of(101L)))
                .thenReturn(List.of());

        assertThrows(ApplicationException.class, () -> orderService.processPaymentCallback(42L, BigDecimal.TEN, true));

        assertEquals(PaymentStatus.UNPAID, order.getPayment().getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        verify(paymentRepository, never()).save(order.getPayment());
    }

    @Test
    void expirePendingOrder_ShouldReleaseReservationAndMarkOrderExpired() {
        Order order = order(BigDecimal.TEN, PaymentStatus.UNPAID);
        order.setReservationExpiresAt(Instant.parse("2026-01-01T00:00:00Z"));
        InventoryBalance balance = balance(1);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));
        when(inventoryBalanceRepository.findAllByVariantIdsForUpdate(List.of(101L)))
                .thenReturn(List.of(balance));

        assertEquals(true, orderService.expirePendingOrder(42L, Instant.parse("2026-01-01T00:00:01Z")));

        assertEquals(OrderStatus.PAYMENT_EXPIRED, order.getStatus());
        assertEquals(0, balance.getReserved());
        assertEquals(1, balance.getOnHand());
    }

    @Test
    void expirePendingOrder_ShouldRecheckStateBeforeTouchingInventory() {
        Order order = order(BigDecimal.TEN, PaymentStatus.UNPAID, OrderStatus.CONFIRMED);
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
                Payment.builder().order(order).status(PaymentStatus.UNPAID).build());
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
        Order order = order(BigDecimal.TEN, PaymentStatus.UNPAID);
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
        ProductVariant variant = ProductVariant.builder().id(101L).build();
        InventoryBalance balance = InventoryBalance.zero(variant);
        balance.adjustOnHand(reserved);
        balance.reserve(reserved);
        return balance;
    }
}
