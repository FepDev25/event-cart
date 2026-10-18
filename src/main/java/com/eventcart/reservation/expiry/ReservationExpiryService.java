package com.eventcart.reservation.expiry;

import com.eventcart.config.ExpiryProperties;
import com.eventcart.eventing.contract.EventType;
import com.eventcart.eventing.contract.ReservationExpiredPayload;
import com.eventcart.eventing.outbox.OutboxWriter;
import com.eventcart.launch.persistence.LaunchJpaRepository;
import com.eventcart.reservation.ReservationStatus;
import com.eventcart.reservation.persistence.BuyerLaunchUsageJpaRepository;
import com.eventcart.reservation.persistence.ReservationEntity;
import com.eventcart.reservation.persistence.ReservationJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Expiración de reservas vencidas: libera el stock exactamente una vez. Cada reserva se procesa en su
 * propia transacción y el paso a {@code EXPIRED} es una actualización condicional
 * ({@code WHERE status = 'RESERVED'}), por lo que no depende de Kafka ni de la ejecución del job.
 */
@Service
public class ReservationExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryService.class);

    public static final String EXPIRED_METRIC = "eventcart.reservations.expired";

    private final ReservationJpaRepository reservations;
    private final LaunchJpaRepository launches;
    private final BuyerLaunchUsageJpaRepository buyerUsage;
    private final OutboxWriter outbox;
    private final ExpiryProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Counter expiredCounter;

    public ReservationExpiryService(PlatformTransactionManager transactionManager,
                                    ReservationJpaRepository reservations,
                                    LaunchJpaRepository launches,
                                    BuyerLaunchUsageJpaRepository buyerUsage,
                                    OutboxWriter outbox,
                                    ExpiryProperties properties,
                                    Clock clock,
                                    MeterRegistry meterRegistry) {
        this.reservations = reservations;
        this.launches = launches;
        this.buyerUsage = buyerUsage;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.expiredCounter = Counter.builder(EXPIRED_METRIC).register(meterRegistry);
    }

    /**
     * Expira un lote de reservas vencidas. Devuelve cuántas se liberaron en esta pasada.
     */
    public int expireDueReservations() {
        Instant now = clock.instant();
        List<ReservationEntity> candidates = reservations
                .findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        ReservationStatus.RESERVED, now, PageRequest.of(0, properties.batchSize()));
        int expired = 0;
        for (ReservationEntity candidate : candidates) {
            UUID reservationId = candidate.getId();
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> expireOne(reservationId)))) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                log.error("Failed to expire reservation id={}", reservationId, ex);
            }
        }
        return expired;
    }

    private boolean expireOne(UUID reservationId) {
        if (reservations.expireIfReserved(reservationId) == 0) {
            return false;
        }
        ReservationEntity reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException("reservation disappeared: " + reservationId));
        if (launches.releaseUnits(reservation.getLaunchId(), reservation.getQuantity()) == 0) {
            throw new IllegalStateException("stock accounting broke while expiring reservation " + reservationId);
        }
        buyerUsage.release(reservation.getBuyerId(), reservation.getLaunchId(), reservation.getQuantity());
        outbox.append(reservationId, EventType.RESERVATION_EXPIRED, new ReservationExpiredPayload(
                reservationId, reservation.getLaunchId(), reservation.getBuyerId(), reservation.getQuantity()));
        expiredCounter.increment();
        log.info("Reservation expired id={} launch={} quantity={}", reservationId,
                reservation.getLaunchId(), reservation.getQuantity());
        return true;
    }
}
