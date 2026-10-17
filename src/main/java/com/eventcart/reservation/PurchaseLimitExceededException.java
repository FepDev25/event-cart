package com.eventcart.reservation;

import com.eventcart.shared.DomainException;

/**
 * El comprador excedería el límite de unidades por lanzamiento.
 */
public class PurchaseLimitExceededException extends DomainException {

    public PurchaseLimitExceededException(String message) {
        super(message);
    }
}
