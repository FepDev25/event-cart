package com.eventcart.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Importe monetario con código ISO-4217. Escala fija de 2 decimales.
 */
public record Money(BigDecimal amount, String currency) {

    private static final Pattern ISO_4217 = Pattern.compile("^[A-Z]{3}$");

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (currency == null || !ISO_4217.matcher(currency).matches()) {
            throw new IllegalArgumentException("currency must be an ISO-4217 code (3 uppercase letters)");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("amount scale must be <= 2");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("cannot add different currencies: " + currency + " and " + other.currency);
        }
        return new Money(amount.add(other.amount), currency);
    }
}
