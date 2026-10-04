package com.xdpsx.ecommerce.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;

class OrderMapperTest {
    @Test
    void customerDetails_ShouldNotExposeCancellationActor() {
        Order order = Order.builder()
                .id(42L)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("100.00"))
                .currency("VND")
                .build();
        order.setPayment(
                Payment.builder().order(order).status(PaymentStatus.PAID).build());
        order.cancel("Fulfillment failed", "admin@example.test", LocalDateTime.parse("2026-01-01T00:00:00"));

        OrderDetailsDTO customerDetails = Mappers.getMapper(OrderMapper.class).fromEntityToCustomerDetails(order);

        assertThat(customerDetails.getCancelledBy()).isNull();
        assertThat(customerDetails.getCancellationReason()).isEqualTo("Fulfillment failed");
    }
}
