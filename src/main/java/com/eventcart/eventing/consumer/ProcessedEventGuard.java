package com.eventcart.eventing.consumer;

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

    public ProcessedEventGuard(ProcessedEventJpaRepository processedEvents) {
        this.processedEvents = processedEvents;
    }

    public boolean tryMarkProcessed(String consumer, UUID eventId) {
        return processedEvents.insertIfAbsent(consumer, eventId) == 1;
    }
}
