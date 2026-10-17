package com.eventcart.shared;

/**
 * Recurso inexistente (o no visible para el solicitante).
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}
