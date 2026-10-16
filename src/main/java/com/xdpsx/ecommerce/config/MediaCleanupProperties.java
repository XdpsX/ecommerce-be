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
@ConfigurationProperties(prefix = "media.cleanup")
public class MediaCleanupProperties {
    private boolean enabled;
    private Duration fixedDelay = Duration.ofMinutes(5);
    private Duration temporaryLifetime = Duration.ofHours(24);
    private Duration retryDelay = Duration.ofMinutes(15);
    private Duration signedTombstoneRecheck = Duration.ofHours(24);
    private int batchSize = 10;
    private int providerRequestBudget = 30;

    @AssertTrue(
            message = "media cleanup durations must be positive, batch size at least 2, and provider budget at least 8")
    public boolean isConfigurationValid() {
        return positive(fixedDelay)
                && positive(temporaryLifetime)
                && positive(retryDelay)
                && positive(signedTombstoneRecheck)
                && batchSize >= 2
                && providerRequestBudget >= 8;
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
