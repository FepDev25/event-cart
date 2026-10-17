package com.eventcart.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    void normalizesScaleToTwoDecimals() {
        assertEquals(new BigDecimal("25.00"), Money.of("25", "USD").amount());
        assertEquals(new BigDecimal("25.50"), Money.of("25.5", "USD").amount());
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("-1", "USD"));
    }

    @Test
    void rejectsNonIsoCurrency() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("1", "usd"));
        assertThrows(IllegalArgumentException.class, () -> Money.of("1", "US"));
        assertThrows(IllegalArgumentException.class, () -> Money.of("1", null));
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("1.234", "USD"));
    }

    @Test
    void multipliesByQuantity() {
        assertEquals(Money.of("50.00", "USD"), Money.of("25", "USD").multiply(2));
    }

    @Test
    void rejectsNegativeMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("25", "USD").multiply(-1));
    }

    @Test
    void addRejectsCurrencyMismatch() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("1", "USD").add(Money.of("1", "EUR")));
    }
}
