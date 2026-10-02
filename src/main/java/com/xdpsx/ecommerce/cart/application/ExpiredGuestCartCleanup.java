package com.xdpsx.ecommerce.cart.application;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.cart.persistence.CartRepository;
import com.xdpsx.ecommerce.config.CartProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExpiredGuestCartCleanup {
    private static final int MAX_BATCHES_PER_RUN = 10;

    private final CartRepository cartRepository;
    private final CartProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.cart.cleanup-fixed-delay-ms:3600000}")
    @Transactional
    public void deleteExpiredGuests() {
        deleteExpiredGuestsInBatches();
    }

    @Transactional
    public int deleteExpiredGuestsNow() {
        return deleteExpiredGuestsInBatches();
    }

    private int deleteExpiredGuestsInBatches() {
        int deleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<Long> ids = cartRepository.findExpiredGuestIds(
                    clock.instant(), PageRequest.of(0, properties.getCleanupBatchSize()));
            if (ids.isEmpty()) break;
            deleted += cartRepository.deleteExpiredGuestsIfStillExpired(ids, clock.instant());
            if (ids.size() < properties.getCleanupBatchSize()) break;
        }
        return deleted;
    }
}
