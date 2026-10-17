package com.eventcart.eventing.contract;

/**
 * Evento desconocido o versión no soportada; debe derivarse al circuito de investigación (DLT).
 */
public class UnsupportedEventException extends RuntimeException {

    public UnsupportedEventException(String message) {
        super(message);
    }
}
