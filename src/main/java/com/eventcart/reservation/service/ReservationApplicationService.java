package com.eventcart.reservation.service;

import com.eventcart.config.IdempotencyProperties;
import com.eventcart.launch.InsufficientStockException;
import com.eventcart.launch.LaunchNotOpenException;
import com.eventcart.launch.LaunchStatus;
import com.eventcart.launch.OutsideSaleWindowException;
import com.eventcart.launch.persistence.LaunchEntity;
import com.eventcart.launch.persistence.LaunchJpaRepository;
import com.eventcart.reservation.PurchaseLimitExceededException;
import com.eventcart.reservation.Reservation;
import com.eventcart.reservation.ReservationStatus;
import com.eventcart.reservation.persistence.BuyerLaunchUsageJpaRepository;
import com.eventcart.reservation.persistence.IdempotencyKeyEntity;
import com.eventcart.reservation.persistence.IdempotencyKeyJpaRepository;
import com.eventcart.reservation.persistence.ReservationEntity;
import com.eventcart.reservation.persistence.ReservationJpaRepository;
import com.eventcart.reservation.persistence.ReservationMapper;
import com.eventcart.shared.DomainException;
import com.eventcart.shared.Hashes;
import com.eventcart.shared.IdempotencyConflictException;
import com.eventcart.shared.InvalidStateTransitionException;
import com.eventcart.shared.Money;
import com.eventcart.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reserva atómica: idempotencia + límite por comprador + captura condicional de stock en una sola
 * transacción PostgreSQL.
 */
@Service
public class ReservationApplicationService {

    private static final String IDEMPOTENCY_SCOPE = "reservation:create";

    private final LaunchJpaRepository launches;
    private final ReservationJpaRepository reservations;
    private final BuyerLaunchUsageJpaRepository buyerUsage;
    private final IdempotencyKeyJpaRepository idempotency;
    private final ReservationMapper mapper;
    private final IdempotencyProperties idempotencyProperties;
    private final Clock clock;

    public ReservationApplicationService(LaunchJpaRepository launches, ReservationJpaRepository reservations,
                                         BuyerLaunchUsageJpaRepository buyerUsage,
                                         IdempotencyKeyJpaRepository idempotency, ReservationMapper mapper,
                                         IdempotencyProperties idempotencyProperties, Clock clock) {
        this.launches = launches;
        this.reservations = reservations;
        this.buyerUsage = buyerUsage;
        this.idempotency = idempotency;
        this.mapper = mapper;
        this.idempotencyProperties = idempotencyProperties;
        this.clock = clock;
    }

    @Transactional
    public Reservation reserve(UUID launchId, UUID buyerId, int quantity, String idempotencyKey) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        Instant now = clock.instant();
        String requestHash = Hashes.sha256("launch=" + launchId + ";buyer=" + buyerId + ";quantity=" + quantity);

        int inserted = idempotency.insertIfAbsent(UUID.randomUUID(), IDEMPOTENCY_SCOPE, idempotencyKey, requestHash,
                now.plus(idempotencyProperties.ttl()));
        if (inserted == 0) {
            IdempotencyKeyEntity existing = idempotency.findByScopeAndIdempotencyKey(IDEMPOTENCY_SCOPE, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("idempotency entry disappeared"));
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new IdempotencyConflictException(
                        "idempotency key '" + idempotencyKey + "' was already used with a different request");
            }
            return reservations.findById(existing.getResourceId())
                    .map(mapper::toDomain)
                    .orElseThrow(() -> new IllegalStateException("idempotent result not found"));
        }

        LaunchEntity launch = launches.findById(launchId)
                .orElseThrow(() -> new NotFoundException("launch not found: " + launchId));

        int limit = launch.getPerBuyerLimit() == null ? Integer.MAX_VALUE : launch.getPerBuyerLimit();
        if (buyerUsage.reserveWithinLimit(buyerId, launchId, quantity, limit) == 0) {
            throw new PurchaseLimitExceededException(
                    "per-buyer limit of " + limit + " exceeded on launch " + launchId);
        }

        if (launches.reserveUnits(launchId, quantity) == 0) {
            throw rejectionReason(launch, now);
        }

        Reservation reservation = Reservation.create(launchId, buyerId, quantity,
                new Money(launch.getUnitPrice(), launch.getCurrency()),
                Duration.ofSeconds(launch.getReservationTtlSeconds()), now);
        ReservationEntity entity = mapper.toEntity(reservation);
        reservations.save(entity);

        idempotency.complete(IDEMPOTENCY_SCOPE, idempotencyKey, entity.getId(), 201, entity.getId().toString());
        return mapper.toDomain(entity);
    }

    @Transactional
    public Reservation cancel(UUID reservationId, UUID buyerId) {
        ReservationEntity reservation = ownedOrNotFound(reservationId, buyerId);

        if (reservations.releaseIfReserved(reservationId) == 0) {
            ReservationStatus current = reservations.findById(reservationId)
                    .map(ReservationEntity::getStatus)
                    .orElse(null);
            throw new InvalidStateTransitionException(
                    "reservation " + reservationId + " cannot be cancelled in status " + current);
        }
        launches.releaseUnits(reservation.getLaunchId(), reservation.getQuantity());
        buyerUsage.release(buyerId, reservation.getLaunchId(), reservation.getQuantity());
        return mapper.toDomain(reservations.findById(reservationId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID reservationId, UUID buyerId) {
        return mapper.toDomain(ownedOrNotFound(reservationId, buyerId));
    }

    @Transactional(readOnly = true)
    public List<Reservation> listMine(UUID buyerId) {
        return reservations.findByBuyerIdOrderByCreatedAtDesc(buyerId).stream().map(mapper::toDomain).toList();
    }

    private ReservationEntity ownedOrNotFound(UUID reservationId, UUID buyerId) {
        ReservationEntity reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("reservation not found: " + reservationId));
        if (!reservation.getBuyerId().equals(buyerId)) {
            throw new NotFoundException("reservation not found: " + reservationId);
        }
        return reservation;
    }

    private DomainException rejectionReason(LaunchEntity launch, Instant now) {
        if (launch.getStatus() != LaunchStatus.OPEN) {
            return new LaunchNotOpenException(
                    "launch " + launch.getId() + " is not open (status=" + launch.getStatus() + ")");
        }
        if (now.isBefore(launch.getOpensAt()) || !now.isBefore(launch.getClosesAt())) {
            return new OutsideSaleWindowException("instant " + now + " is outside the sale window");
        }
        return new InsufficientStockException("not enough stock on launch " + launch.getId());
    }
}
