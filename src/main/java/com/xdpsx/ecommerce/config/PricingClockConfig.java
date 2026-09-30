package com.xdpsx.ecommerce.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PricingClockConfig {
    @Bean
    public Clock pricingClock() {
        return Clock.systemUTC();
    }
}
