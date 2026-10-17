package com.eventcart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eventcart.checkout")
public record CheckoutProperties(Duration latency, Duration timeout) {

    public CheckoutProperties {
        if (latency == null) {
            latency = Duration.ofMillis(100);
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(1);
        }
    }
}
