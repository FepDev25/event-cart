package com.eventcart.eventing.consumer;

import com.eventcart.eventing.contract.EventType;
import com.eventcart.eventing.contract.UnsupportedEventException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Deserializa el envelope y valida tipo/versión. Tipos o versiones no soportados lanzan
 * {@link UnsupportedEventException} para que el error handler los derive a la DLT.
 */
@Component
public class EventDeserializer {

    private final ObjectMapper objectMapper;

    public EventDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ConsumedEvent parse(String json) {
        ConsumedEvent event = objectMapper.readValue(json, ConsumedEvent.class);
        EventType type = EventType.fromWireName(event.type())
                .orElseThrow(() -> new UnsupportedEventException("unknown event type: " + event.type()));
        if (event.version() != type.version()) {
            throw new UnsupportedEventException(
                    "unsupported version " + event.version() + " for event type " + event.type());
        }
        return event;
    }

    public <T> T payload(ConsumedEvent event, Class<T> payloadType) {
        return objectMapper.readValue(event.payload().toString(), payloadType);
    }
}
