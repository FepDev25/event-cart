package com.eventcart.eventing.contract;

import java.time.Instant;

/**
 * Envelope autocontenido de un evento de negocio.
 *
 * <pre>
 * { eventId, type, version, aggregateId, occurredAt, payload }
 * </pre>
 */
public record EventEnvelope(
        String eventId,
        String type,
        int version,
        String aggregateId,
        Instant occurredAt,
        Object payload) {
}
