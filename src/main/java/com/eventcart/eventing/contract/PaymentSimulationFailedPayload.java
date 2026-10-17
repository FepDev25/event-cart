package com.eventcart.eventing.contract;

import java.util.UUID;

public record PaymentSimulationFailedPayload(
        UUID orderId,
        UUID reservationId,
        UUID launchId,
        UUID buyerId,
        int quantity,
        String reason) {
}
