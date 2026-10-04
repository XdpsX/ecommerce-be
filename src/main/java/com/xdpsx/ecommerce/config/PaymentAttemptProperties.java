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
@ConfigurationProperties(prefix = "app.payment-attempt")
public class PaymentAttemptProperties {
    private Duration lifetime = Duration.ofMinutes(15);

    @AssertTrue(message = "payment attempt lifetime must be positive")
    public boolean isLifetimePositive() {
        return lifetime != null && !lifetime.isZero() && !lifetime.isNegative();
    }
}
