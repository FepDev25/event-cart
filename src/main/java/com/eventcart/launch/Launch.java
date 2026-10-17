package com.eventcart.launch;

import com.eventcart.shared.InvalidStateTransitionException;
import com.eventcart.shared.Money;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raíz del contador de inventario de un lanzamiento.
 *
 * <p>Invariante: {@code availableUnits + reservedUnits + confirmedUnits == totalStock}, con todos
 * los contadores no negativos. El precio, el stock y la ventana temporal quedan congelados al
 * pasar a {@link LaunchStatus#OPEN}.
 */
@Getter
public class Launch {

    private final UUID id;
    private String title;
    private String description;
    private String productName;
    private String productRef;
    private Money unitPrice;
    private int totalStock;
    private int availableUnits;
    private int reservedUnits;
    private int confirmedUnits;
    private LaunchStatus status;
    private Instant opensAt;
    private Instant closesAt;
    private Duration reservationTtl;
    private Integer perBuyerLimit;
    private final UUID sellerId;
    private final Instant createdAt;
    private Instant updatedAt;

    private Launch(UUID id, String title, String description, String productName, String productRef,
                   Money unitPrice, int totalStock, Instant opensAt, Instant closesAt, Duration reservationTtl,
                   Integer perBuyerLimit, UUID sellerId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = requireNonBlank(title, "title");
        this.description = description;
        this.productName = requireNonBlank(productName, "productName");
        this.productRef = productRef;
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        if (totalStock < 0) {
            throw new IllegalArgumentException("totalStock must be >= 0");
        }
        this.totalStock = totalStock;
        this.availableUnits = totalStock;
        this.reservedUnits = 0;
        this.confirmedUnits = 0;
        this.status = LaunchStatus.DRAFT;
        this.opensAt = Objects.requireNonNull(opensAt, "opensAt");
        this.closesAt = requireAfter(opensAt, closesAt);
        this.reservationTtl = requirePositive(reservationTtl, "reservationTtl");
        this.perBuyerLimit = requirePositiveOrNull(perBuyerLimit);
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
        assertInvariant();
    }

    public static Launch create(String title, String description, String productName, String productRef,
                                Money unitPrice, int totalStock, Instant opensAt, Instant closesAt,
                                Duration reservationTtl, Integer perBuyerLimit, UUID sellerId, Instant createdAt) {
        return new Launch(UUID.randomUUID(), title, description, productName, productRef, unitPrice, totalStock,
                opensAt, closesAt, reservationTtl, perBuyerLimit, sellerId, createdAt);
    }

    // --- ciclo de vida -------------------------------------------------------

    public void schedule(Instant now) {
        requireStatus("schedule", LaunchStatus.DRAFT);
        this.status = LaunchStatus.SCHEDULED;
        touch(now);
    }

    public void open(Instant now) {
        requireStatus("open", LaunchStatus.DRAFT, LaunchStatus.SCHEDULED);
        this.status = LaunchStatus.OPEN;
        touch(now);
    }

    public void close(Instant now) {
        requireStatus("close", LaunchStatus.OPEN);
        this.status = LaunchStatus.CLOSED;
        touch(now);
    }

    public void cancel(Instant now) {
        if (status.isTerminal()) {
            throw new InvalidStateTransitionException("launch " + id + " is already " + status);
        }
        this.status = LaunchStatus.CANCELLED;
        touch(now);
    }

    /**
     * Edita los datos configurables. Prohibido una vez abierta la venta.
     */
    public void reconfigure(String title, String description, String productName, String productRef,
                            Money unitPrice, Instant opensAt, Instant closesAt, Duration reservationTtl,
                            Integer perBuyerLimit, Instant now) {
        if (status == LaunchStatus.OPEN || status.isTerminal()) {
            throw new InvalidStateTransitionException("cannot reconfigure launch " + id + " in status " + status);
        }
        this.title = requireNonBlank(title, "title");
        this.description = description;
        this.productName = requireNonBlank(productName, "productName");
        this.productRef = productRef;
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        this.opensAt = Objects.requireNonNull(opensAt, "opensAt");
        this.closesAt = requireAfter(opensAt, closesAt);
        this.reservationTtl = requirePositive(reservationTtl, "reservationTtl");
        this.perBuyerLimit = requirePositiveOrNull(perBuyerLimit);
        touch(now);
    }

    // --- inventario ----------------------------------------------------------

    public void assertReservable(Instant now, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        if (status != LaunchStatus.OPEN) {
            throw new LaunchNotOpenException("launch " + id + " is not open (status=" + status + ")");
        }
        if (now.isBefore(opensAt) || !now.isBefore(closesAt)) {
            throw new OutsideSaleWindowException(
                    "instant " + now + " is outside sale window [" + opensAt + ", " + closesAt + ")");
        }
    }

    public void reserveUnits(int quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        if (availableUnits < quantity) {
            throw new InsufficientStockException(
                    "requested " + quantity + " but only " + availableUnits + " available on launch " + id);
        }
        availableUnits -= quantity;
        reservedUnits += quantity;
        touch(now);
        assertInvariant();
    }

    public void releaseUnits(int quantity, Instant now) {
        requireReserved(quantity);
        reservedUnits -= quantity;
        availableUnits += quantity;
        touch(now);
        assertInvariant();
    }

    public void confirmUnits(int quantity, Instant now) {
        requireReserved(quantity);
        reservedUnits -= quantity;
        confirmedUnits += quantity;
        touch(now);
        assertInvariant();
    }

    private void requireReserved(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        if (reservedUnits < quantity) {
            throw new InsufficientStockException(
                    "cannot release/confirm " + quantity + " units; only " + reservedUnits + " reserved on launch " + id);
        }
    }

    public void assertInvariant() {
        if (availableUnits < 0 || reservedUnits < 0 || confirmedUnits < 0) {
            throw new IllegalStateException("negative stock counters on launch " + id);
        }
        if (availableUnits + reservedUnits + confirmedUnits != totalStock) {
            throw new IllegalStateException("stock invariant violated on launch " + id + ": available=" + availableUnits
                    + " reserved=" + reservedUnits + " confirmed=" + confirmedUnits + " total=" + totalStock);
        }
    }

    // --- helpers -------------------------------------------------------------

    private void requireStatus(String action, LaunchStatus... allowed) {
        for (LaunchStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new InvalidStateTransitionException("cannot " + action + " launch " + id + " in status " + status);
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static Instant requireAfter(Instant opensAt, Instant closesAt) {
        Objects.requireNonNull(closesAt, "closesAt");
        if (!closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException("closesAt must be after opensAt");
        }
        return closesAt;
    }

    private static Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static Integer requirePositiveOrNull(Integer value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("perBuyerLimit must be positive when set");
        }
        return value;
    }
}
