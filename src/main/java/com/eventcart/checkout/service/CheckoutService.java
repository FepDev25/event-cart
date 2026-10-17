package com.eventcart.checkout.service;

import com.eventcart.config.CheckoutProperties;
import com.eventcart.config.IdempotencyProperties;
import com.eventcart.eventing.contract.EventType;
import com.eventcart.eventing.contract.OrderConfirmedPayload;
import com.eventcart.eventing.contract.PaymentSimulationFailedPayload;
import com.eventcart.eventing.outbox.OutboxWriter;
import com.eventcart.launch.persistence.LaunchJpaRepository;
import com.eventcart.order.Order;
import com.eventcart.order.OrderStatus;
import com.eventcart.order.persistence.OrderEntity;
import com.eventcart.order.persistence.OrderJpaRepository;
import com.eventcart.order.persistence.OrderMapper;
import com.eventcart.reservation.ReservationStatus;
import com.eventcart.reservation.persistence.BuyerLaunchUsageJpaRepository;
import com.eventcart.reservation.persistence.IdempotencyKeyEntity;
import com.eventcart.reservation.persistence.IdempotencyKeyJpaRepository;
import com.eventcart.reservation.persistence.ReservationEntity;
import com.eventcart.reservation.persistence.ReservationJpaRepository;
import com.eventcart.shared.Hashes;
import com.eventcart.shared.IdempotencyConflictException;
import com.eventcart.shared.InvalidStateTransitionException;
import com.eventcart.shared.Money;
import com.eventcart.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Checkout simulado. La simulación de pago ocurre <em>fuera</em> de la transacción para no retener
 * locks durante la latencia; el resultado se aplica en una segunda transacción.
 */
@Service
public class CheckoutService {

    private static final String IDEMPOTENCY_SCOPE = "checkout";

    private final LaunchJpaRepository launches;
    private final ReservationJpaRepository reservations;
    private final BuyerLaunchUsageJpaRepository buyerUsage;
    private final OrderJpaRepository orders;
    private final IdempotencyKeyJpaRepository idempotency;
    private final IdempotencyProperties idempotencyProperties;
    private final OrderMapper orderMapper;
    private final PaymentSimulator paymentSimulator;
    private final CheckoutProperties checkoutProperties;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final TransactionTemplate tx;

    public CheckoutService(PlatformTransactionManager transactionManager, LaunchJpaRepository launches,
                           ReservationJpaRepository reservations, BuyerLaunchUsageJpaRepository buyerUsage,
                           OrderJpaRepository orders, IdempotencyKeyJpaRepository idempotency,
                           IdempotencyProperties idempotencyProperties, OrderMapper orderMapper,
                           PaymentSimulator paymentSimulator, CheckoutProperties checkoutProperties,
                           OutboxWriter outbox, Clock clock) {
        this.launches = launches;
        this.reservations = reservations;
        this.buyerUsage = buyerUsage;
        this.orders = orders;
        this.idempotency = idempotency;
        this.idempotencyProperties = idempotencyProperties;
        this.orderMapper = orderMapper;
        this.paymentSimulator = paymentSimulator;
        this.checkoutProperties = checkoutProperties;
        this.outbox = outbox;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public Order checkout(UUID reservationId, UUID buyerId, String paymentToken, String idempotencyKey) {
        String requestHash = Hashes.sha256(
                "reservation=" + reservationId + ";buyer=" + buyerId + ";token=" + paymentToken);

        BeginOutcome begin = Objects.requireNonNull(
                tx.execute(status -> begin(reservationId, buyerId, idempotencyKey, requestHash)));

        if (!begin.fresh()) {
            return orderMapper.toDomain(orders.findById(begin.orderId()).orElseThrow());
        }

        PaymentResult result = simulateWithTimeout(paymentToken);
        return orderMapper.toDomain(Objects.requireNonNull(tx.execute(status -> applyResult(begin.orderId(), result))));
    }

    private BeginOutcome begin(UUID reservationId, UUID buyerId, String idempotencyKey, String requestHash) {
        Instant now = clock.instant();
        int inserted = idempotency.insertIfAbsent(UUID.randomUUID(), IDEMPOTENCY_SCOPE, idempotencyKey, requestHash,
                now.plus(idempotencyProperties.ttl()));
        if (inserted == 0) {
            IdempotencyKeyEntity existing = idempotency.findByScopeAndIdempotencyKey(IDEMPOTENCY_SCOPE, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("idempotency entry disappeared"));
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new IdempotencyConflictException(
                        "idempotency key '" + idempotencyKey + "' was already used with a different request");
            }
            return new BeginOutcome(existing.getResourceId(), false);
        }

        ReservationEntity reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("reservation not found: " + reservationId));
        if (!reservation.getBuyerId().equals(buyerId)) {
            throw new NotFoundException("reservation not found: " + reservationId);
        }

        Optional<OrderEntity> existingOrder = orders.findByReservationId(reservationId);
        if (existingOrder.isPresent()) {
            UUID orderId = existingOrder.get().getId();
            idempotency.complete(IDEMPOTENCY_SCOPE, idempotencyKey, orderId, 200, orderId.toString());
            return new BeginOutcome(orderId, false);
        }

        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new InvalidStateTransitionException(
                    "reservation " + reservationId + " is " + reservation.getStatus() + "; cannot checkout");
        }
        if (!now.isBefore(reservation.getExpiresAt())) {
            throw new InvalidStateTransitionException(
                    "reservation " + reservationId + " expired at " + reservation.getExpiresAt());
        }

        Order order = Order.pending(reservationId, reservation.getLaunchId(), buyerId, reservation.getQuantity(),
                new Money(reservation.getUnitPrice(), reservation.getCurrency()), now);
        OrderEntity entity = orderMapper.toEntity(order);
        orders.save(entity);
        idempotency.complete(IDEMPOTENCY_SCOPE, idempotencyKey, entity.getId(), 202, entity.getId().toString());
        return new BeginOutcome(entity.getId(), true);
    }

    private OrderEntity applyResult(UUID orderId, PaymentResult result) {
        OrderEntity order = orders.findById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PENDING) {
            return order;
        }
        ReservationEntity reservation = reservations.findById(order.getReservationId()).orElseThrow();

        switch (result) {
            case APPROVED -> {
                if (reservations.confirmIfReserved(order.getReservationId()) == 0) {
                    orders.failIfPending(orderId, "APPROVED", "reservation could not be confirmed");
                } else {
                    launches.confirmUnits(reservation.getLaunchId(), reservation.getQuantity());
                    buyerUsage.confirm(order.getBuyerId(), reservation.getLaunchId(), reservation.getQuantity());
                    orders.confirmIfPending(orderId, "APPROVED");
                    outbox.append(orderId, EventType.ORDER_CONFIRMED, new OrderConfirmedPayload(
                            orderId, order.getReservationId(), order.getLaunchId(), order.getBuyerId(),
                            order.getQuantity(), order.getTotalAmount(), order.getCurrency()));
                }
            }
            case DECLINED -> {
                orders.failIfPending(orderId, "DECLINED", "payment declined");
                if (reservations.releaseIfReserved(order.getReservationId()) != 0) {
                    launches.releaseUnits(reservation.getLaunchId(), reservation.getQuantity());
                    buyerUsage.release(order.getBuyerId(), reservation.getLaunchId(), reservation.getQuantity());
                }
                outbox.append(orderId, EventType.PAYMENT_SIMULATION_FAILED, new PaymentSimulationFailedPayload(
                        orderId, order.getReservationId(), order.getLaunchId(), order.getBuyerId(),
                        order.getQuantity(), "DECLINED"));
            }
            case TIMEOUT -> {
                // Resultado indeterminado: el pedido queda PENDING para reconciliación (M4).
            }
        }
        return orders.findById(orderId).orElseThrow();
    }

    private PaymentResult simulateWithTimeout(String paymentToken) {
        try {
            return CompletableFuture.supplyAsync(() -> paymentSimulator.simulate(paymentToken))
                    .orTimeout(checkoutProperties.timeout().toMillis(), TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                return PaymentResult.TIMEOUT;
            }
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    private record BeginOutcome(UUID orderId, boolean fresh) {
    }
}
