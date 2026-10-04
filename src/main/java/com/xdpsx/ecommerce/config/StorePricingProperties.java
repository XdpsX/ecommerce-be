package com.xdpsx.ecommerce.config;

import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.store")
public class StorePricingProperties {
    @Pattern(regexp = "VND", message = "app.store.currency must be VND while VNPay is the payment provider")
    private String currency = "VND";

    /**
     * M1 is a single-currency store and VNPay currently submits every payment as VND. Rejecting any other
     * configured value during property binding prevents the catalog from advertising a unit the payment provider
     * does not understand.
     */
    public void setCurrency(String currency) {
        if (!"VND".equals(currency)) {
            throw new IllegalArgumentException("app.store.currency must be VND while VNPay is the payment provider");
        }
        this.currency = currency;
    }
}
