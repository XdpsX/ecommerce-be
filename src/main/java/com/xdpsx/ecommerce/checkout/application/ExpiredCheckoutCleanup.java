package com.xdpsx.ecommerce.checkout.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.config.CheckoutProperties;
import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiredCheckoutCleanup {
    private static final int MAX_BATCHES_PER_RUN = 10;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final CheckoutProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.checkout.cleanup-fixed-delay-ms:60000}")
    public void expirePendingOrders() {
        expirePendingOrdersInBatches();
    }

    public int expirePendingOrdersNow() {
        return expirePendingOrdersInBatches();
    }

    private int expirePendingOrdersInBatches() {
        int expired = 0;
        long lastSeenId = 0L;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            Instant cutoff = clock.instant();
            List<Long> ids = orderRepository.findExpiredPendingIds(
                    cutoff, lastSeenId, PageRequest.of(0, properties.getCleanupBatchSize()));
            if (ids.isEmpty()) break;
            for (Long id : ids) {
                lastSeenId = id;
                try {
                    if (orderService.expirePendingOrder(id, cutoff)) expired++;
                } catch (RuntimeException exception) {
                    log.warn("Could not expire pending Order {} during cleanup", id, exception);
                }
            }
            if (ids.size() < properties.getCleanupBatchSize()) break;
        }
        return expired;
    }
}
