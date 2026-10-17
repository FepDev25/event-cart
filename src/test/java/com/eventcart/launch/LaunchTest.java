package com.eventcart.launch;

import com.eventcart.shared.InvalidStateTransitionException;
import com.eventcart.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LaunchTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static Launch launch(int stock, Integer perBuyerLimit) {
        return Launch.create("Drop", "desc", "Tee", "SKU-1", Money.of("25.00", "USD"), stock,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), Duration.ofMinutes(10), perBuyerLimit,
                UUID.randomUUID(), NOW);
    }

    @Test
    void startsAsDraftWithFullAvailability() {
        Launch launch = launch(10, null);
        assertEquals(LaunchStatus.DRAFT, launch.getStatus());
        assertEquals(10, launch.getTotalStock());
        assertEquals(10, launch.getAvailableUnits());
        assertEquals(0, launch.getReservedUnits());
        assertEquals(0, launch.getConfirmedUnits());
        launch.assertInvariant();
    }

    @Test
    void runsTheHappyLifecycle() {
        Launch launch = launch(10, null);
        launch.schedule(NOW);
        assertEquals(LaunchStatus.SCHEDULED, launch.getStatus());
        launch.open(NOW);
        assertEquals(LaunchStatus.OPEN, launch.getStatus());
        launch.close(NOW);
        assertEquals(LaunchStatus.CLOSED, launch.getStatus());
    }

    @Test
    void closesOnlyWhenOpen() {
        Launch launch = launch(10, null);
        assertThrows(InvalidStateTransitionException.class, () -> launch.close(NOW));
    }

    @Test
    void cancelsFromDraft() {
        Launch launch = launch(10, null);
        launch.cancel(NOW);
        assertEquals(LaunchStatus.CANCELLED, launch.getStatus());
        assertThrows(InvalidStateTransitionException.class, () -> launch.open(NOW));
    }

    @Test
    void reserveUnitsReducesAvailability() {
        Launch launch = launch(10, null);
        launch.open(NOW);
        launch.reserveUnits(3, NOW);
        assertEquals(7, launch.getAvailableUnits());
        assertEquals(3, launch.getReservedUnits());
        launch.assertInvariant();
    }

    @Test
    void reserveBeyondAvailableThrowsAndKeepsCounters() {
        Launch launch = launch(10, null);
        launch.open(NOW);
        assertThrows(InsufficientStockException.class, () -> launch.reserveUnits(11, NOW));
        assertEquals(10, launch.getAvailableUnits());
        assertEquals(0, launch.getReservedUnits());
    }

    @Test
    void releaseReturnsUnitsToAvailable() {
        Launch launch = launch(10, null);
        launch.open(NOW);
        launch.reserveUnits(4, NOW);
        launch.releaseUnits(4, NOW);
        assertEquals(10, launch.getAvailableUnits());
        assertEquals(0, launch.getReservedUnits());
        launch.assertInvariant();
    }

    @Test
    void confirmMovesReservedToConfirmed() {
        Launch launch = launch(10, null);
        launch.open(NOW);
        launch.reserveUnits(4, NOW);
        launch.confirmUnits(4, NOW);
        assertEquals(6, launch.getAvailableUnits());
        assertEquals(0, launch.getReservedUnits());
        assertEquals(4, launch.getConfirmedUnits());
        launch.assertInvariant();
    }

    @Test
    void reservingRequiresOpenStatus() {
        Launch launch = launch(10, null);
        assertThrows(LaunchNotOpenException.class, () -> launch.assertReservable(NOW, 1));
    }

    @Test
    void reservingRequiresInsideSaleWindow() {
        Launch launch = launch(10, null);
        launch.open(NOW);
        assertThrows(OutsideSaleWindowException.class,
                () -> launch.assertReservable(NOW.plusSeconds(7200), 1));
    }

    @Test
    void reconfigureIsForbiddenOnceOpen() {
        Launch launch = launch(10, null);
        launch.open(NOW);
        assertThrows(InvalidStateTransitionException.class, () -> launch.reconfigure("New", "d", "P", "r",
                Money.of("30.00", "USD"), NOW, NOW.plusSeconds(3600), Duration.ofMinutes(5), null, NOW));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> Launch.create("t", "d", "p", null,
                Money.of("1", "USD"), -1, NOW, NOW.plusSeconds(60), Duration.ofMinutes(1), null, UUID.randomUUID(), NOW));
        assertThrows(IllegalArgumentException.class, () -> Launch.create("t", "d", "p", null,
                Money.of("1", "USD"), 1, NOW, NOW, Duration.ofMinutes(1), null, UUID.randomUUID(), NOW));
        assertThrows(IllegalArgumentException.class, () -> Launch.create("t", "d", "p", null,
                Money.of("1", "USD"), 1, NOW, NOW.plusSeconds(60), Duration.ZERO, null, UUID.randomUUID(), NOW));
        assertThrows(IllegalArgumentException.class, () -> Launch.create("t", "d", "p", null,
                Money.of("1", "USD"), 1, NOW, NOW.plusSeconds(60), Duration.ofMinutes(1), 0, UUID.randomUUID(), NOW));
    }

    @Test
    void invariantHoldsAcrossManyOperations() {
        Launch launch = launch(10, null);
        launch.open(NOW);
        launch.reserveUnits(2, NOW);
        launch.reserveUnits(3, NOW);
        launch.releaseUnits(1, NOW);
        launch.confirmUnits(3, NOW);
        assertEquals(6, launch.getAvailableUnits());
        assertEquals(1, launch.getReservedUnits());
        assertEquals(3, launch.getConfirmedUnits());
        launch.assertInvariant();
    }
}
