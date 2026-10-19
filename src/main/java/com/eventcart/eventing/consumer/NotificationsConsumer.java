package com.eventcart.eventing.consumer;

import com.eventcart.eventing.contract.Topics;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!worker & !api")
public class NotificationsConsumer {

    private final EventDeserializer deserializer;
    private final NotificationsHandler handler;

    public NotificationsConsumer(EventDeserializer deserializer, NotificationsHandler handler) {
        this.deserializer = deserializer;
        this.handler = handler;
    }

    @KafkaListener(topics = {Topics.RESERVATION, Topics.ORDER}, groupId = NotificationsHandler.CONSUMER)
    public void onMessage(String message) {
        handler.handle(deserializer.parse(message));
    }
}
