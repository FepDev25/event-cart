package com.eventcart.eventing.consumer;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Vista de un evento ya leído de Kafka.
 */
public record ConsumedEvent(
        String eventId,
        String type,
        int version,
        String aggregateId,
        Instant occurredAt,
        JsonNode payload) {
}
