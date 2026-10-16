package com.xdpsx.ecommerce.config;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "media.reconciliation")
public class MediaReconciliationProperties {
    private boolean enabled = false;
    private Duration fixedDelay = Duration.ofMinutes(1);
    private Duration processingTimeout = Duration.ofMinutes(10);
    private int batchSize = 10;
    private int maxProviderRequestsPerRun = 20;
    private int maxDerivedPagesPerAsset = 2;
    private Duration retryDelay = Duration.ofMinutes(5);
    private Duration providerErrorBackoff = Duration.ofMinutes(15);
    private Duration maxRetryDelay = Duration.ofHours(24);
    private Duration processingRecoveryHorizon = Duration.ofDays(7);
    private Duration pendingUploadRetryWindow = Duration.ofHours(24);
    private Duration pendingUploadRetryDelay = Duration.ofMinutes(15);
    private Duration pendingUploadRetryMaxDelay = Duration.ofHours(24);
    private Duration legacyLookupRaceWindow = Duration.ofMinutes(10);

    @AssertTrue(message = "media reconciliation limits and durations must be positive")
    public boolean areLimitsPositive() {
        return fixedDelay != null
                && !fixedDelay.isNegative()
                && !fixedDelay.isZero()
                && positive(processingTimeout)
                && batchSize > 0
                && maxProviderRequestsPerRun > 0
                && maxDerivedPagesPerAsset > 0
                && positive(retryDelay)
                && positive(providerErrorBackoff)
                && positive(maxRetryDelay)
                && positive(processingRecoveryHorizon)
                && positive(pendingUploadRetryWindow)
                && positive(pendingUploadRetryDelay)
                && positive(pendingUploadRetryMaxDelay)
                && positive(legacyLookupRaceWindow);
    }

    @AssertTrue(message = "media reconciliation max retry delay must be at least base and provider backoff")
    public boolean isMaxRetryDelayValid() {
        return maxRetryDelay != null
                && retryDelay != null
                && providerErrorBackoff != null
                && maxRetryDelay.compareTo(retryDelay) >= 0
                && maxRetryDelay.compareTo(providerErrorBackoff) >= 0
                && pendingUploadRetryMaxDelay != null
                && pendingUploadRetryDelay != null
                && pendingUploadRetryMaxDelay.compareTo(pendingUploadRetryDelay) >= 0;
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
