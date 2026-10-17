package com.eventcart.reservation;

import com.eventcart.launch.InsufficientStockException;
import com.eventcart.launch.Launch;
import com.eventcart.launch.LaunchNotOpenException;
import com.eventcart.launch.OutsideSaleWindowException;
import com.eventcart.shared.InvalidStateTransitionException;
import com.eventcart.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private final ReservationService service = new ReservationService();

    private static Launch openLaunch(int stock, Integer limit) {
        Launch launch = Launch.create("Drop", "desc", "Tee", "SKU-1", Money.of("25.00", "USD"), stock,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), Duration.ofMinutes(10), limit, UUID.randomUUID(), NOW);
        launch.open(NOW);
        return launch;
    }

    @Test
    void reservesAndUpdatesInventory() {
        Launch launch = openLaunch(10, null);
        Reservation r = service.reserve(launch, UUID.randomUUID(), 3, 0, NOW);

        assertEquals(ReservationStatus.RESERVED, r.getStatus());
        assertEquals(3, r.getQuantity());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), r.getExpiresAt());
        assertEquals(7, launch.getAvailableUnits());
        assertEquals(3, launch.getReservedUnits());
        launch.assertInvariant();
    }

    @Test
    void rejectsReservationBeyondAvailableWithoutSideEffects() {
        Launch launch = openLaunch(10, null);
        assertThrows(InsufficientStockException.class, () -> service.reserve(launch, UUID.randomUUID(), 11, 0, NOW));
        assertEquals(10, launch.getAvailableUnits());
        assertEquals(0, launch.getReservedUnits());
        launch.assertInvariant();
    }

    @Test
    void rejectsReservationWhenNotOpen() {
        Launch launch = Launch.create("Drop", "desc", "Tee", "SKU-1", Money.of("25.00", "USD"), 10,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), Duration.ofMinutes(10), null, UUID.randomUUID(), NOW);
        assertThrows(LaunchNotOpenException.class, () -> service.reserve(launch, UUID.randomUUID(), 1, 0, NOW));
    }

    @Test
    void rejectsReservationOutsideSaleWindow() {
        Launch launch = openLaunch(10, null);
        assertThrows(OutsideSaleWindowException.class,
                () -> service.reserve(launch, UUID.randomUUID(), 1, 0, NOW.plusSeconds(7200)));
    }

    @Test
    void enforcesPerBuyerLimit() {
        Launch launch = openLaunch(10, 2);
        assertThrows(PurchaseLimitExceededException.class,
                () -> service.reserve(launch, UUID.randomUUID(), 3, 0, NOW));
        assertThrows(PurchaseLimitExceededException.class,
                () -> service.reserve(launch, UUID.randomUUID(), 1, 2, NOW));
        assertEquals(10, launch.getAvailableUnits());
    }

    @Test
    void rejectsNonPositiveQuantity() {
        Launch launch = openLaunch(10, null);
        assertThrows(IllegalArgumentException.class, () -> service.reserve(launch, UUID.randomUUID(), 0, 0, NOW));
    }

    @Test
    void confirmMovesInventoryToConfirmed() {
        Launch launch = openLaunch(10, null);
        Reservation r = service.reserve(launch, UUID.randomUUID(), 3, 0, NOW);
        service.confirm(launch, r, NOW.plusSeconds(30));

        assertEquals(ReservationStatus.CONFIRMED, r.getStatus());
        assertEquals(7, launch.getAvailableUnits());
        assertEquals(0, launch.getReservedUnits());
        assertEquals(3, launch.getConfirmedUnits());
        launch.assertInvariant();
    }

    @Test
    void expiryReturnsInventoryOnceAndBlocksFurtherTransitions() {
        Launch launch = openLaunch(10, null);
        Reservation r = service.reserve(launch, UUID.randomUUID(), 3, 0, NOW);
        service.expire(launch, r, NOW.plus(Duration.ofMinutes(10)));

        assertEquals(ReservationStatus.EXPIRED, r.getStatus());
        assertEquals(10, launch.getAvailableUnits());
        assertEquals(0, launch.getReservedUnits());
        launch.assertInvariant();

        assertThrows(InvalidStateTransitionException.class, () -> service.expire(launch, r, NOW.plusSeconds(900)));
    }

    @Test
    void cannotConfirmExpiredReservation() {
        Launch launch = openLaunch(10, null);
        Reservation r = service.reserve(launch, UUID.randomUUID(), 3, 0, NOW);
        assertThrows(InvalidStateTransitionException.class,
                () -> service.confirm(launch, r, NOW.plus(Duration.ofMinutes(10))));
    }
}
