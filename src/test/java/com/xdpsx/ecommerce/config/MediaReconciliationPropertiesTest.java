package com.xdpsx.ecommerce.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class MediaReconciliationPropertiesTest {
    @Test
    void defaults_ShouldKeepSchedulerDisabledAndSetBoundedRecoveryPolicy() {
        MediaReconciliationProperties properties = new MediaReconciliationProperties();

        assertFalse(properties.isEnabled());
        assertTrue(properties.areLimitsPositive());
        assertTrue(properties.isMaxRetryDelayValid());
        org.junit.jupiter.api.Assertions.assertEquals(Duration.ofDays(7), properties.getProcessingRecoveryHorizon());
        org.junit.jupiter.api.Assertions.assertEquals(20, properties.getMaxProviderRequestsPerRun());
    }

    @Test
    void validation_ShouldRejectInvalidBudgetsAndOverflowingRetryConfiguration() {
        MediaReconciliationProperties properties = new MediaReconciliationProperties();
        properties.setMaxProviderRequestsPerRun(0);
        properties.setPendingUploadRetryMaxDelay(Duration.ofMinutes(1));

        assertFalse(properties.areLimitsPositive());
        assertFalse(properties.isMaxRetryDelayValid());
    }
}
