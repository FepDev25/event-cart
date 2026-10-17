package com.eventcart.shared;

/**
 * Falta el header {@code Idempotency-Key} en una operación que lo exige.
 */
public class MissingIdempotencyKeyException extends DomainException {

    public MissingIdempotencyKeyException(String message) {
        super(message);
    }
}
