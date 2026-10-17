package com.eventcart.reservation.web;

import com.eventcart.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID launchId,
        int quantity,
        BigDecimal unitPrice,
        String currency,
        BigDecimal totalAmount,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdAt) {
}
