package com.eventcart.shared;

/**
 * Acción no permitida para el usuario autenticado (p. ej. no es el dueño del recurso).
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }
}
