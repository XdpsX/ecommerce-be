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
@ConfigurationProperties(prefix = "app.checkout")
public class CheckoutProperties {
    private Duration reservationLifetime = Duration.ofMinutes(15);
    private int cleanupBatchSize = 100;
    private long cleanupFixedDelayMs = 60_000L;

    @AssertTrue(message = "reservation lifetime must be positive")
    public boolean isReservationLifetimePositive() {
        return reservationLifetime != null && !reservationLifetime.isZero() && !reservationLifetime.isNegative();
    }

    @AssertTrue(message = "cleanup batch size must be positive")
    public boolean isCleanupBatchSizePositive() {
        return cleanupBatchSize > 0;
    }

    @AssertTrue(message = "cleanup fixed delay must be positive")
    public boolean isCleanupFixedDelayPositive() {
        return cleanupFixedDelayMs > 0;
    }
}
