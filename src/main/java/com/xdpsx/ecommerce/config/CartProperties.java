package com.xdpsx.ecommerce.config;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.cart")
public class CartProperties {
    private Duration guestLifetime = Duration.ofDays(30);

    @NotBlank
    private String guestCookieName = "guest_cart";

    @NotBlank
    private String guestCookiePath = "/cart";

    private boolean guestCookieSecure = true;

    @NotBlank
    @Pattern(regexp = "Strict|Lax|None", message = "must be Strict, Lax, or None")
    private String guestCookieSameSite = "Lax";

    @NotBlank
    private String guardHeaderName = "X-Cart-Request";

    @NotBlank
    private String guardHeaderValue = "1";

    private int cleanupBatchSize = 100;
    private long cleanupFixedDelayMs = 3_600_000L;

    @AssertTrue(message = "must be positive")
    public boolean isGuestLifetimePositive() {
        return guestLifetime != null && !guestLifetime.isZero() && !guestLifetime.isNegative();
    }

    @AssertTrue(message = "must be positive")
    public boolean isCleanupBatchSizePositive() {
        return cleanupBatchSize > 0;
    }

    @AssertTrue(message = "must be positive")
    public boolean isCleanupFixedDelayPositive() {
        return cleanupFixedDelayMs > 0;
    }
}
