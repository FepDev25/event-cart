package com.eventcart.web;

import com.eventcart.config.RateLimitProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de ventana fija en memoria. Suficiente para el prototipo; no distribuido.
 */
@Component
public class InMemoryRateLimiter {

    private final int capacity;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(RateLimitProperties properties) {
        this.capacity = properties.capacity();
        this.windowMillis = properties.window().toMillis();
    }

    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.startMillis >= windowMillis) {
                return new Window(now, 1);
            }
            existing.count++;
            return existing;
        });
        return window.count <= capacity;
    }

    private static final class Window {
        private final long startMillis;
        private int count;

        private Window(long startMillis, int count) {
            this.startMillis = startMillis;
            this.count = count;
        }
    }
}
