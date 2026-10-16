package com.xdpsx.ecommerce.media.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.media.application.MediaCleanupService;

import lombok.RequiredArgsConstructor;

/** The single opt-in scheduling entry point for all Media cleanup paths. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "media.cleanup", name = "enabled", havingValue = "true")
public class MediaCleanUpScheduler {
    private final MediaCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${media.cleanup.fixed-delay:5m}")
    public void runCleanup() {
        cleanupService.runCleanup();
    }
}
