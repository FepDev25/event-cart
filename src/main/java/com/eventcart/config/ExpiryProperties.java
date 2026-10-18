package com.eventcart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eventcart.expiry")
public record ExpiryProperties(Duration pollInterval, int batchSize) {

    public ExpiryProperties {
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(5);
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
    }
}
