package com.eventcart.launch;

import com.eventcart.shared.DomainException;

/**
 * El instante de la operación está fuera de la ventana [opensAt, closesAt) del lanzamiento.
 */
public class OutsideSaleWindowException extends DomainException {

    public OutsideSaleWindowException(String message) {
        super(message);
    }
}
