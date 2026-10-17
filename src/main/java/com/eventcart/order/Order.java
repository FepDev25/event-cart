package com.eventcart.order;

import com.eventcart.shared.InvalidStateTransitionException;
import com.eventcart.shared.Money;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pedido resultante del checkout de una reserva (1:1). Conserva el snapshot comercial.
 *
 * <pre>
 * PENDING ─▶ CONFIRMED (terminal)
 *         └▶ FAILED    (terminal)
 * </pre>
 */
@Getter
public class Order {

    private final UUID id;
    private final UUID reservationId;
    private final UUID launchId;
    private final UUID buyerId;
    private final int quantity;
    private final Money unitPrice;
    private final Money totalAmount;
    private OrderStatus status;
    private String paymentResult;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private Order(UUID id, UUID reservationId, UUID launchId, UUID buyerId, int quantity, Money unitPrice,
                  Money totalAmount, OrderStatus status, String paymentResult, String failureReason,
                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.launchId = launchId;
        this.buyerId = buyerId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.status = status;
        this.paymentResult = paymentResult;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Order pending(UUID reservationId, UUID launchId, UUID buyerId, int quantity, Money unitPrice,
                                Instant now) {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(launchId, "launchId");
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(now, "now");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        return new Order(UUID.randomUUID(), reservationId, launchId, buyerId, quantity, unitPrice,
                unitPrice.multiply(quantity), OrderStatus.PENDING, null, null, now, now);
    }

    /**
     * Rehidrata un pedido ya persistido preservando {@code id}, estado y snapshot comercial.
     */
    public static Order reconstitute(UUID id, UUID reservationId, UUID launchId, UUID buyerId, int quantity,
                                     Money unitPrice, Money totalAmount, OrderStatus status, String paymentResult,
                                     String failureReason, Instant createdAt, Instant updatedAt) {
        return new Order(id, reservationId, launchId, buyerId, quantity, unitPrice, totalAmount, status,
                paymentResult, failureReason, createdAt, updatedAt);
    }

    public void confirm(String paymentResult, Instant now) {
        requirePending("confirm");
        this.status = OrderStatus.CONFIRMED;
        this.paymentResult = paymentResult;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void fail(String paymentResult, String reason, Instant now) {
        requirePending("fail");
        this.status = OrderStatus.FAILED;
        this.paymentResult = paymentResult;
        this.failureReason = reason;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    private void requirePending(String action) {
        if (status != OrderStatus.PENDING) {
            throw new InvalidStateTransitionException("cannot " + action + " order " + id + " in status " + status);
        }
    }
}
