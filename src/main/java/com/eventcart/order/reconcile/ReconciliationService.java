package com.eventcart.order.reconcile;

import com.eventcart.config.ExpiryProperties;
import com.eventcart.config.ReconciliationProperties;
import com.eventcart.eventing.contract.EventType;
import com.eventcart.eventing.contract.PaymentSimulationFailedPayload;
import com.eventcart.eventing.outbox.OutboxWriter;
import com.eventcart.launch.persistence.LaunchJpaRepository;
import com.eventcart.order.OrderStatus;
import com.eventcart.order.persistence.OrderEntity;
import com.eventcart.order.persistence.OrderJpaRepository;
import com.eventcart.reservation.persistence.BuyerLaunchUsageJpaRepository;
import com.eventcart.reservation.persistence.ReservationJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reconciliación periódica: resuelve checkouts {@code PENDING} cuyo resultado quedó indeterminado
 * (los marca {@code FAILED} y libera la reserva si seguía {@code RESERVED}) y expone una señal de
 * reservas vencidas aún no liberadas. Cada pedido se procesa en su propia transacción y la liberación
 * es condicional, de modo que no se libera stock dos veces.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    public static final String FAILED_ORDERS_METRIC = "eventcart.reconciliation.orders_failed";
    public static final String STALE_RESERVED_METRIC = "eventcart.reconciliation.stale_reserved";

    private final OrderJpaRepository orders;
    private final ReservationJpaRepository reservations;
    private final LaunchJpaRepository launches;
    private final BuyerLaunchUsageJpaRepository buyerUsage;
    private final OutboxWriter outbox;
    private final ReconciliationProperties properties;
    private final ExpiryProperties expiryProperties;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Counter failedOrders;
    private final AtomicLong staleReserved = new AtomicLong();

    public ReconciliationService(OrderJpaRepository orders, ReservationJpaRepository reservations,
                                 LaunchJpaRepository launches, BuyerLaunchUsageJpaRepository buyerUsage,
                                 OutboxWriter outbox, ReconciliationProperties properties,
                                 ExpiryProperties expiryProperties, PlatformTransactionManager transactionManager,
                                 Clock clock, MeterRegistry meterRegistry) {
        this.orders = orders;
        this.reservations = reservations;
        this.launches = launches;
        this.buyerUsage = buyerUsage;
        this.outbox = outbox;
        this.properties = properties;
        this.expiryProperties = expiryProperties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.failedOrders = Counter.builder(FAILED_ORDERS_METRIC).register(meterRegistry);
        Gauge.builder(STALE_RESERVED_METRIC, staleReserved, AtomicLong::get).register(meterRegistry);
    }

    /**
     * Reconcilia un lote. Devuelve cuántos pedidos pendientes se resolvieron en esta pasada.
     */
    public int reconcile() {
        Instant now = clock.instant();
        updateStaleReserved(now);
        List<OrderEntity> pending = orders.findByStatusAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                OrderStatus.PENDING, now.minus(properties.pendingTimeout()),
                PageRequest.of(0, properties.batchSize()));
        int failed = 0;
        for (OrderEntity order : pending) {
            UUID orderId = order.getId();
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> failPendingOrder(orderId)))) {
                    failed++;
                }
            } catch (RuntimeException ex) {
                log.error("Failed to reconcile order id={}", orderId, ex);
            }
        }
        return failed;
    }

    private void updateStaleReserved(Instant now) {
        Instant threshold = now.minus(expiryProperties.graceWindow());
        long stale = reservations.countExpirable(threshold, threshold);
        staleReserved.set(stale);
        if (stale > 0) {
            log.warn("Reconciliation detected {} reservation(s) past expiry not yet released", stale);
        }
    }

    private boolean failPendingOrder(UUID orderId) {
        if (orders.failIfPending(orderId, "TIMEOUT", "reconciled: payment result unknown") == 0) {
            return false;
        }
        OrderEntity order = orders.findById(orderId).orElseThrow();
        if (reservations.releaseIfReserved(order.getReservationId()) != 0) {
            if (launches.releaseUnits(order.getLaunchId(), order.getQuantity()) == 0) {
                throw new IllegalStateException("stock accounting broke while reconciling order " + orderId);
            }
            buyerUsage.release(order.getBuyerId(), order.getLaunchId(), order.getQuantity());
        }
        outbox.append(orderId, EventType.PAYMENT_SIMULATION_FAILED, new PaymentSimulationFailedPayload(
                orderId, order.getReservationId(), order.getLaunchId(), order.getBuyerId(),
                order.getQuantity(), "TIMEOUT"));
        failedOrders.increment();
        log.info("Reconciled pending order id={} reservation={}", orderId, order.getReservationId());
        return true;
    }
}
