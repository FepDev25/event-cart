package com.eventcart.reservation;

/**
 * Estado de una reserva. Todos los estados salvo {@link #RESERVED} son terminales.
 */
public enum ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED,
    EXPIRED;

    public boolean isTerminal() {
        return this != RESERVED;
    }
}
