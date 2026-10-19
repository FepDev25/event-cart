package com.eventcart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eventcart.expiry")
public record ExpiryProperties(Duration pollInterval, int batchSize, Duration graceWindow) {

    public ExpiryProperties {
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(5);
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
        if (graceWindow == null) {
            graceWindow = Duration.ofSeconds(2);
        }
    }
}
