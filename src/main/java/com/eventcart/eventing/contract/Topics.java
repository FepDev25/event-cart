package com.eventcart.eventing.contract;

/**
 * Topics de Kafka por dominio. La clave de partición es el {@code aggregateId}.
 */
public final class Topics {

    public static final String RESERVATION = "eventcart.reservation";
    public static final String ORDER = "eventcart.order";

    private Topics() {
    }

    public static String deadLetter(String topic) {
        return topic + ".DLT";
    }
}
