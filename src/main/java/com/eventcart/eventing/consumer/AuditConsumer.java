package com.eventcart.eventing.consumer;

import com.eventcart.eventing.contract.Topics;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!worker & !api")
public class AuditConsumer {

    private final EventDeserializer deserializer;
    private final AuditHandler handler;

    public AuditConsumer(EventDeserializer deserializer, AuditHandler handler) {
        this.deserializer = deserializer;
        this.handler = handler;
    }

    @KafkaListener(topics = {Topics.RESERVATION, Topics.ORDER}, groupId = AuditHandler.CONSUMER)
    public void onMessage(String message) {
        handler.handle(deserializer.parse(message));
    }
}
