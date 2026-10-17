package com.eventcart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eventcart.rate-limit")
public record RateLimitProperties(int capacity, Duration window) {

    public RateLimitProperties {
        if (capacity <= 0) {
            capacity = 100;
        }
        if (window == null) {
            window = Duration.ofMinutes(1);
        }
    }
}
