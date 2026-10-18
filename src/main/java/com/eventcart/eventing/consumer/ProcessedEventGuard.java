package com.eventcart.eventing.consumer;

import com.eventcart.eventing.metrics.EventingMetrics;
import com.eventcart.eventing.projection.ProcessedEventJpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deduplicación persistente por consumidor: devuelve {@code true} solo la primera vez que ve un
 * evento. Debe invocarse dentro de la misma transacción que el efecto.
 */
@Component
public class ProcessedEventGuard {

    private final ProcessedEventJpaRepository processedEvents;
    private final EventingMetrics metrics;

    public ProcessedEventGuard(ProcessedEventJpaRepository processedEvents, EventingMetrics metrics) {
        this.processedEvents = processedEvents;
        this.metrics = metrics;
    }

    public boolean tryMarkProcessed(String consumer, UUID eventId) {
        boolean first = processedEvents.insertIfAbsent(consumer, eventId) == 1;
        if (!first) {
            metrics.duplicateIgnored(consumer);
        }
        return first;
    }
}
