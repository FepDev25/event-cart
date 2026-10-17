package com.eventcart.launch;

import com.eventcart.shared.DomainException;

/**
 * Se intentó reservar en un lanzamiento que no está abierto.
 */
public class LaunchNotOpenException extends DomainException {

    public LaunchNotOpenException(String message) {
        super(message);
    }
}
