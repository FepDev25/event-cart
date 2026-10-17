package com.eventcart.order;

/**
 * Estado de un pedido. {@code PENDING} representa un checkout iniciado con resultado aún indeterminado.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    FAILED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
