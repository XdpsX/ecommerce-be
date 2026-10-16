package com.xdpsx.ecommerce.media.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.media.application.MediaReconciliationService;

import lombok.extern.slf4j.Slf4j;

/** Opt-in scheduler for bounded Cloudinary state reconciliation. */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "media.reconciliation", name = "enabled", havingValue = "true")
public class MediaReconciliationScheduler {
    private final MediaReconciliationService service;

    public MediaReconciliationScheduler(MediaReconciliationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${media.reconciliation.fixed-delay:1m}")
    public void reconcile() {
        MediaReconciliationService.TickResult result = service.runOnce();
        log.info(
                "Media reconciliation tick completed, providerRequests={}, pendingVisited={}, processingVisited={}",
                result.providerRequests(),
                result.pendingRowsVisited(),
                result.processingRowsVisited());
    }
}
