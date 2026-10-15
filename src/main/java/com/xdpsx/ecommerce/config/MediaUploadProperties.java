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

    @AssertTrue(message = "intent lifetime must be positive")
    public boolean isIntentLifetimePositive() {
        return intentLifetime != null && !intentLifetime.isZero() && !intentLifetime.isNegative();
    }

    @AssertTrue(message = "maximum file size must be positive")
    public boolean isMaxFileSizePositive() {
        return maxFileSize != null && maxFileSize.toBytes() > 0;
    }
}
