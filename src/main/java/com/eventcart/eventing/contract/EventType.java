package com.eventcart.eventing.contract;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo de eventos de negocio de M3 con su nombre de transporte, versión y topic.
 */
public enum EventType {
    RESERVATION_CREATED("ReservationCreated", 1, Topics.RESERVATION),
    RESERVATION_RELEASED("ReservationReleased", 1, Topics.RESERVATION),
    ORDER_CONFIRMED("OrderConfirmed", 1, Topics.ORDER),
    PAYMENT_SIMULATION_FAILED("PaymentSimulationFailed", 1, Topics.ORDER);

    private final String wireName;
    private final int version;
    private final String topic;

    EventType(String wireName, int version, String topic) {
        this.wireName = wireName;
        this.version = version;
        this.topic = topic;
    }

    public String wireName() {
        return wireName;
    }

    public int version() {
        return version;
    }

    public String topic() {
        return topic;
    }

    public static Optional<EventType> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(type -> type.wireName.equals(wireName)).findFirst();
    }
}
