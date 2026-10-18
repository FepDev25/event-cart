package com.eventcart.eventing.contract;

import java.util.UUID;

public record ReservationExpiredPayload(
        UUID reservationId,
        UUID launchId,
        UUID buyerId,
        int quantity) {
}
