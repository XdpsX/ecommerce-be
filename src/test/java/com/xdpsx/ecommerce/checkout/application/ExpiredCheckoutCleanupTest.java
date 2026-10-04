package com.xdpsx.ecommerce.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.xdpsx.ecommerce.config.CheckoutProperties;
import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;

@ExtendWith(MockitoExtension.class)
class ExpiredCheckoutCleanupTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Test
    void expirePendingOrdersNow_ShouldContinueAfterPoisonCandidate() {
        CheckoutProperties properties = new CheckoutProperties();
        properties.setCleanupBatchSize(2);
        when(orderRepository.findExpiredPendingIds(eq(NOW), eq(0L), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(11L, 12L));
        when(orderRepository.findExpiredPendingIds(eq(NOW), eq(12L), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(13L));
        when(orderService.expirePendingOrder(11L, NOW)).thenReturn(true);
        when(orderService.expirePendingOrder(12L, NOW)).thenThrow(new IllegalStateException("poison"));
        when(orderService.expirePendingOrder(13L, NOW)).thenReturn(true);

        ExpiredCheckoutCleanup cleanup =
                new ExpiredCheckoutCleanup(orderRepository, orderService, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(cleanup.expirePendingOrdersNow()).isEqualTo(2);
        verify(orderService).expirePendingOrder(11L, NOW);
        verify(orderService).expirePendingOrder(12L, NOW);
        verify(orderService).expirePendingOrder(13L, NOW);
        verify(orderRepository).findExpiredPendingIds(NOW, 0L, PageRequest.of(0, 2));
        verify(orderRepository).findExpiredPendingIds(NOW, 12L, PageRequest.of(0, 2));
    }
}
