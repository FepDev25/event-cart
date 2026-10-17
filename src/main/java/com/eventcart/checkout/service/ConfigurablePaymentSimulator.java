package com.eventcart.checkout.service;

import com.eventcart.config.CheckoutProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Simulador determinista: el token {@code APPROVE}/{@code DECLINE}/{@code TIMEOUT} fija el resultado y
 * la latencia es configurable. {@code TIMEOUT} tarda más que el timeout del servicio para provocar
 * un resultado indeterminado.
 */
@Component
public class ConfigurablePaymentSimulator implements PaymentSimulator {

    private final CheckoutProperties properties;

    public ConfigurablePaymentSimulator(CheckoutProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentResult simulate(String paymentToken) {
        if (paymentToken == null) {
            throw new IllegalArgumentException("paymentToken is required");
        }
        return switch (paymentToken.toUpperCase(Locale.ROOT)) {
            case "APPROVE" -> {
                sleep(properties.latency());
                yield PaymentResult.APPROVED;
            }
            case "DECLINE" -> {
                sleep(properties.latency());
                yield PaymentResult.DECLINED;
            }
            case "TIMEOUT" -> {
                sleep(properties.timeout().multipliedBy(2));
                yield PaymentResult.TIMEOUT;
            }
            default -> throw new IllegalArgumentException("unknown payment token: " + paymentToken);
        };
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
