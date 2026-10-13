package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.net.URI;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "media.cloudinary")
public class CloudinaryMediaProperties {
    @NotBlank
    private String eagerNotificationUrl;

    @AssertTrue(message = "must be a valid HTTPS URL")
    public boolean isEagerNotificationUrlHttps() {
        if (eagerNotificationUrl == null || eagerNotificationUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(eagerNotificationUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
