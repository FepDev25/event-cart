package com.eventcart.eventing.consumer;

import com.eventcart.eventing.contract.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SalesProjectionConsumer {

    private final EventDeserializer deserializer;
    private final SalesProjectionHandler handler;

    public SalesProjectionConsumer(EventDeserializer deserializer, SalesProjectionHandler handler) {
        this.deserializer = deserializer;
        this.handler = handler;
    }

    @KafkaListener(topics = Topics.ORDER, groupId = SalesProjectionHandler.CONSUMER)
    public void onMessage(String message) {
        handler.handle(deserializer.parse(message));
    }
}
