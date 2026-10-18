package com.eventcart.eventing.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Observabilidad mínima de eventing (sección 10 del diseño M3): contadores de eventos publicados,
 * fallos del publicador, duplicados ignorados por consumidor y eventos derivados a la DLT.
 */
@Component
public class EventingMetrics {

    public static final String OUTBOX_PUBLISHED = "eventcart.outbox.published";
    public static final String OUTBOX_PUBLISH_FAILURES = "eventcart.outbox.publish.failures";
    public static final String DUPLICATES_IGNORED = "eventcart.events.duplicates";
    public static final String DEAD_LETTERED = "eventcart.events.dlt";

    private final MeterRegistry registry;

    public EventingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void outboxPublished() {
        Counter.builder(OUTBOX_PUBLISHED).register(registry).increment();
    }

    public void outboxPublishFailed() {
        Counter.builder(OUTBOX_PUBLISH_FAILURES).register(registry).increment();
    }

    public void duplicateIgnored(String consumer) {
        Counter.builder(DUPLICATES_IGNORED).tag("consumer", consumer).register(registry).increment();
    }

    public void deadLettered() {
        Counter.builder(DEAD_LETTERED).register(registry).increment();
    }
}
