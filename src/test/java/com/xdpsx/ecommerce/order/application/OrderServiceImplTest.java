package com.xdpsx.ecommerce.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.order.domain.Order;
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

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void processPaymentCallback_ShouldMarkUnpaidOrderPaidAfterAmountAndStatusValidation() {
        Order order = order(BigDecimal.TEN, PaymentStatus.UNPAID);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));

        orderService.processPaymentCallback(42L, new BigDecimal("10.00"), true);

        assertEquals(PaymentStatus.PAID, order.getPayment().getStatus());
        assertEquals(PaymentMethod.VNPAY, order.getPayment().getPaymentMethod());
        verify(paymentRepository).save(order.getPayment());
    }

    @Test
    void processPaymentCallback_ShouldBeIdempotentForAlreadyPaidOrder() {
        Order order = order(BigDecimal.TEN, PaymentStatus.PAID);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));

        orderService.processPaymentCallback(42L, BigDecimal.TEN, true);

        verify(paymentRepository, never()).save(order.getPayment());
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

    private static Order order(BigDecimal amount, PaymentStatus status) {
        Order order = Order.builder().id(42L).totalAmount(amount).build();
        order.setPayment(Payment.builder().order(order).status(status).build());
        return order;
    }
}
