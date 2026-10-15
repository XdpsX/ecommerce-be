package com.xdpsx.ecommerce.config;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "media.upload")
public class MediaUploadProperties {
    private Duration intentLifetime = Duration.ofHours(1);
    private DataSize maxFileSize = DataSize.ofMegabytes(2);
    private Duration authorizationLifetime = Duration.ofHours(1);
    private Duration completionGrace = Duration.ofMinutes(10);
    private Duration clockSkewTolerance = Duration.ofMinutes(2);
    private Duration replayGrace = Duration.ofMinutes(10);

    @AssertTrue(message = "intent lifetime must be positive")
    public boolean isIntentLifetimePositive() {
        return intentLifetime != null && !intentLifetime.isZero() && !intentLifetime.isNegative();
    }

    @AssertTrue(message = "maximum file size must be positive")
    public boolean isMaxFileSizePositive() {
        return maxFileSize != null && maxFileSize.toBytes() > 0;
    }

    @AssertTrue(message = "authorization lifetime must equal Cloudinary's one-hour signature window")
    public boolean isAuthorizationLifetimeCloudinaryWindow() {
        return Duration.ofHours(1).equals(authorizationLifetime);
    }

    @AssertTrue(message = "completion grace, clock skew tolerance and replay grace must be non-negative")
    public boolean areUploadWindowsNonNegative() {
        return isNonNegative(completionGrace) && isNonNegative(clockSkewTolerance) && isNonNegative(replayGrace);
    }

    private static boolean isNonNegative(Duration duration) {
        return duration != null && !duration.isNegative();
    }
}
