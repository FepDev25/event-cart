package com.eventcart.launch;

import com.eventcart.shared.DomainException;

/**
 * No hay unidades disponibles suficientes para satisfacer la reserva.
 */
public class InsufficientStockException extends DomainException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
