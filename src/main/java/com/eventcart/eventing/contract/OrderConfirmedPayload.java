package com.eventcart.eventing.contract;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderConfirmedPayload(
        UUID orderId,
        UUID reservationId,
        UUID launchId,
        UUID buyerId,
        int quantity,
        BigDecimal totalAmount,
        String currency) {
}
