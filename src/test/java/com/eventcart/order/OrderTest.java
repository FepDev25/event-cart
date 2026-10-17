package com.eventcart.order;

import com.eventcart.shared.InvalidStateTransitionException;
import com.eventcart.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static Order pending() {
        return Order.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3, Money.of("25.00", "USD"), NOW);
    }

    @Test
    void startsPendingWithSnapshotTotal() {
        Order order = pending();
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(Money.of("75.00", "USD"), order.getTotalAmount());
    }

    @Test
    void confirms() {
        Order order = pending();
        order.confirm("APPROVED", NOW.plusSeconds(5));
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals("APPROVED", order.getPaymentResult());
    }

    @Test
    void fails() {
        Order order = pending();
        order.fail("DECLINED", "insufficient funds", NOW.plusSeconds(5));
        assertEquals(OrderStatus.FAILED, order.getStatus());
        assertEquals("DECLINED", order.getPaymentResult());
        assertEquals("insufficient funds", order.getFailureReason());
    }

    @Test
    void cannotTransitionFromTerminalState() {
        Order order = pending();
        order.fail("TIMEOUT", "no response", NOW);
        assertThrows(InvalidStateTransitionException.class, () -> order.confirm("APPROVED", NOW));
    }
}
