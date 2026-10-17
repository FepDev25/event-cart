package com.eventcart.reservation;

import com.eventcart.shared.InvalidStateTransitionException;
import com.eventcart.shared.Money;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reserva temporal de unidades. Guarda un <em>snapshot</em> comercial (precio y total) tomado al
 * crear, para que cambios posteriores del lanzamiento no alteren su historia.
 *
 * <pre>
 * RESERVED ─▶ CONFIRMED (terminal)
 *          ├─▶ RELEASED  (terminal)
 *          └─▶ EXPIRED   (terminal)
 * </pre>
 */
@Getter
public class Reservation {

    private final UUID id;
    private final UUID launchId;
    private final UUID buyerId;
    private final int quantity;
    private final Money unitPrice;
    private final Money totalAmount;
    private ReservationStatus status;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Reservation(UUID id, UUID launchId, UUID buyerId, int quantity, Money unitPrice, Money totalAmount,
                        ReservationStatus status, Instant expiresAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.launchId = launchId;
        this.buyerId = buyerId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Reservation create(UUID launchId, UUID buyerId, int quantity, Money unitPrice, Duration ttl,
                                     Instant now) {
        Objects.requireNonNull(launchId, "launchId");
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(now, "now");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        return new Reservation(UUID.randomUUID(), launchId, buyerId, quantity, unitPrice,
                unitPrice.multiply(quantity), ReservationStatus.RESERVED, now.plus(ttl), now, now);
    }

    /**
     * Rehidrata una reserva ya persistida preservando {@code id}, estado y snapshot comercial.
     */
    public static Reservation reconstitute(UUID id, UUID launchId, UUID buyerId, int quantity, Money unitPrice,
                                           Money totalAmount, ReservationStatus status, Instant expiresAt,
                                           Instant createdAt, Instant updatedAt) {
        return new Reservation(id, launchId, buyerId, quantity, unitPrice, totalAmount, status, expiresAt, createdAt,
                updatedAt);
    }

    public void confirm(Instant now) {
        requireReserved("confirm");
        if (!now.isBefore(expiresAt)) {
            throw new InvalidStateTransitionException("cannot confirm reservation " + id + ": expired at " + expiresAt);
        }
        transition(ReservationStatus.CONFIRMED, now);
    }

    public void release(Instant now) {
        requireReserved("release");
        transition(ReservationStatus.RELEASED, now);
    }

    public void expire(Instant now) {
        requireReserved("expire");
        if (now.isBefore(expiresAt)) {
            throw new InvalidStateTransitionException("cannot expire reservation " + id + " before " + expiresAt);
        }
        transition(ReservationStatus.EXPIRED, now);
    }

    public boolean isExpiredAt(Instant now) {
        return status == ReservationStatus.RESERVED && !now.isBefore(expiresAt);
    }

    private void requireReserved(String action) {
        if (status != ReservationStatus.RESERVED) {
            throw new InvalidStateTransitionException("cannot " + action + " reservation " + id + " in status " + status);
        }
    }

    private void transition(ReservationStatus target, Instant now) {
        this.status = target;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }
}
