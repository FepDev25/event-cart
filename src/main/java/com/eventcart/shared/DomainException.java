package com.eventcart.shared;

/**
 * Violación de una regla del dominio.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
