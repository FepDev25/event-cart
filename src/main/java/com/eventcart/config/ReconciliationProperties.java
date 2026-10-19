package com.eventcart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eventcart.reconciliation")
public record ReconciliationProperties(Duration pollInterval, int batchSize, Duration pendingTimeout) {

    public ReconciliationProperties {
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(10);
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
        if (pendingTimeout == null) {
            pendingTimeout = Duration.ofMinutes(1);
        }
    }
}
