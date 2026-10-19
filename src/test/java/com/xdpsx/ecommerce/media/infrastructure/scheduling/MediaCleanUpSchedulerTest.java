package com.xdpsx.ecommerce.media.infrastructure.scheduling;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.media.application.cleanup.MediaCleanupService;

class MediaCleanUpSchedulerTest {
    @Test
    void runCleanup_ShouldDelegateToSingleCleanupService() {
        MediaCleanupService service = mock(MediaCleanupService.class);
        new MediaCleanUpScheduler(service).runCleanup();
        verify(service).runCleanup();
    }
}
