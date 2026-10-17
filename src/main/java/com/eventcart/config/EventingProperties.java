package com.eventcart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eventcart.outbox")
public record EventingProperties(Duration pollInterval, int batchSize) {

    public EventingProperties {
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(1);
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
    }
}
