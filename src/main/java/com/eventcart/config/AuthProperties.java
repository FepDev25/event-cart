package com.eventcart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eventcart.auth")
public record AuthProperties(Duration sessionTtl, int bcryptStrength) {

    public AuthProperties {
        if (sessionTtl == null) {
            sessionTtl = Duration.ofHours(24);
        }
        if (bcryptStrength <= 0) {
            bcryptStrength = 10;
        }
    }
}
