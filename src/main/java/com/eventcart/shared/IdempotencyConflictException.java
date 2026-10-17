package com.eventcart.shared;

/**
 * La misma clave de idempotencia se reutilizó con un contenido incompatible.
 */
public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
