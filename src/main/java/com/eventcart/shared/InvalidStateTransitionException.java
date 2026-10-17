package com.eventcart.shared;

/**
 * Transición de estado no permitida.
 */
public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
