package com.xdpsx.ecommerce.config;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.auth-session")
public class AuthSessionProperties {
    @NotBlank
    private String expectedIssuer = "xdpsx.com";

    @NotNull
    private Duration localAccessTokenLifetime = Duration.ofMinutes(15);

    @NotNull
    private Duration refreshSessionLifetime = Duration.ofDays(30);

    @NotBlank
    private String refreshCookieName = "refresh_session";

    @NotBlank
    private String refreshCookiePath = "/auth";

    private boolean refreshCookieSecure = true;

    @NotBlank
    @Pattern(regexp = "Strict|Lax|None", message = "must be Strict, Lax, or None")
    private String refreshCookieSameSite = "Strict";

    @NotBlank
    private String guardHeaderName = "X-Session-Request";

    @NotBlank
    private String guardHeaderValue = "1";

    @AssertTrue(message = "must be positive")
    public boolean isLocalAccessTokenLifetimePositive() {
        return localAccessTokenLifetime != null
                && !localAccessTokenLifetime.isZero()
                && !localAccessTokenLifetime.isNegative();
    }

    @AssertTrue(message = "must be positive")
    public boolean isRefreshSessionLifetimePositive() {
        return refreshSessionLifetime != null
                && !refreshSessionLifetime.isZero()
                && !refreshSessionLifetime.isNegative();
    }
}
