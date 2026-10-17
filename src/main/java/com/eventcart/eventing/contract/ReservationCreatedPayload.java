package com.eventcart.eventing.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationCreatedPayload(
        UUID reservationId,
        UUID launchId,
        UUID buyerId,
        int quantity,
        BigDecimal unitPrice,
        String currency,
        BigDecimal totalAmount,
        Instant expiresAt) {
}
