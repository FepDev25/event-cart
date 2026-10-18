package com.eventcart.eventing.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventingMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EventingMetrics metrics = new EventingMetrics(registry);

    @Test
    void countsPublishedAndFailedPublications() {
        metrics.outboxPublished();
        metrics.outboxPublished();
        metrics.outboxPublishFailed();

        assertEquals(2.0, registry.get(EventingMetrics.OUTBOX_PUBLISHED).counter().count());
        assertEquals(1.0, registry.get(EventingMetrics.OUTBOX_PUBLISH_FAILURES).counter().count());
    }

    @Test
    void countsIgnoredDuplicatesPerConsumer() {
        metrics.duplicateIgnored("eventcart-audit");
        metrics.duplicateIgnored("eventcart-audit");
        metrics.duplicateIgnored("eventcart-sales");

        assertEquals(2.0, registry.get(EventingMetrics.DUPLICATES_IGNORED)
                .tag("consumer", "eventcart-audit").counter().count());
        assertEquals(1.0, registry.get(EventingMetrics.DUPLICATES_IGNORED)
                .tag("consumer", "eventcart-sales").counter().count());
    }

    @Test
    void countsDeadLetteredEvents() {
        metrics.deadLettered();

        assertEquals(1.0, registry.get(EventingMetrics.DEAD_LETTERED).counter().count());
    }
}
