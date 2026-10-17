package com.eventcart.checkout.service;

/**
 * Pago simulado. La implementación decide el resultado a partir de un token ficticio.
 */
public interface PaymentSimulator {

    PaymentResult simulate(String paymentToken);
}
