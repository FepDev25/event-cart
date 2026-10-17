package com.eventcart.reservation;

import com.eventcart.shared.InvalidStateTransitionException;
import com.eventcart.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);

    private static Reservation reservation() {
        return Reservation.create(UUID.randomUUID(), UUID.randomUUID(), 3, Money.of("25.00", "USD"), TTL, NOW);
    }

    @Test
    void createsWithSnapshotAndExpiry() {
        Reservation r = reservation();
        assertEquals(ReservationStatus.RESERVED, r.getStatus());
        assertEquals(Money.of("25.00", "USD"), r.getUnitPrice());
        assertEquals(Money.of("75.00", "USD"), r.getTotalAmount());
        assertEquals(NOW.plus(TTL), r.getExpiresAt());
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> Reservation.create(
                UUID.randomUUID(), UUID.randomUUID(), 0, Money.of("1", "USD"), TTL, NOW));
    }

    @Test
    void confirmsBeforeExpiry() {
        Reservation r = reservation();
        r.confirm(NOW.plusSeconds(60));
        assertEquals(ReservationStatus.CONFIRMED, r.getStatus());
    }

    @Test
    void releases() {
        Reservation r = reservation();
        r.release(NOW);
        assertEquals(ReservationStatus.RELEASED, r.getStatus());
    }

    @Test
    void expiresAfterTtl() {
        Reservation r = reservation();
        r.expire(NOW.plus(TTL));
        assertEquals(ReservationStatus.EXPIRED, r.getStatus());
    }

    @Test
    void cannotTransitionFromTerminalState() {
        Reservation r = reservation();
        r.confirm(NOW);
        assertThrows(InvalidStateTransitionException.class, () -> r.release(NOW));
        assertThrows(InvalidStateTransitionException.class, () -> r.expire(NOW.plus(TTL)));
    }

    @Test
    void cannotConfirmExpiredReservation() {
        Reservation r = reservation();
        assertThrows(InvalidStateTransitionException.class, () -> r.confirm(NOW.plus(TTL)));
    }

    @Test
    void cannotExpireBeforeTtl() {
        Reservation r = reservation();
        assertThrows(InvalidStateTransitionException.class, () -> r.expire(NOW));
    }

    @Test
    void reportsExpiry() {
        Reservation r = reservation();
        assertFalse(r.isExpiredAt(NOW.plusSeconds(60)));
        assertTrue(r.isExpiredAt(NOW.plus(TTL)));
        r.confirm(NOW);
        assertFalse(r.isExpiredAt(NOW.plus(TTL)));
    }
}
