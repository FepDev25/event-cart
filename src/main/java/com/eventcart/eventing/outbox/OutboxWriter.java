package com.eventcart.eventing.outbox;

import com.eventcart.eventing.contract.EventEnvelope;
import com.eventcart.eventing.contract.EventType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.UUID;

/**
 * Registra eventos en el outbox <strong>dentro de la transacción de negocio</strong>: el evento solo
 * existe si el cambio de estado se comprometió.
 */
@Component
public class OutboxWriter {

    private final OutboxJpaRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(OutboxJpaRepository outbox, ObjectMapper objectMapper, Clock clock) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void append(UUID aggregateId, EventType type, Object payload) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                type.wireName(),
                type.version(),
                aggregateId.toString(),
                clock.instant(),
                payload);

        OutboxEntity entity = new OutboxEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateId(aggregateId);
        entity.setEventType(type.wireName());
        entity.setPayload(objectMapper.writeValueAsString(envelope));
        entity.setOccurredAt(envelope.occurredAt());
        entity.setCreatedAt(clock.instant());
        outbox.save(entity);
    }
}
