package com.eventcart.eventing.consumer;

import com.eventcart.eventing.contract.OrderConfirmedPayload;
import com.eventcart.eventing.contract.PaymentSimulationFailedPayload;
import com.eventcart.eventing.contract.ReservationCreatedPayload;
import com.eventcart.eventing.contract.ReservationExpiredPayload;
import com.eventcart.eventing.contract.ReservationReleasedPayload;
import com.eventcart.eventing.projection.NotificationEntity;
import com.eventcart.eventing.projection.NotificationJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Efecto observable: crea una notificación ficticia por comprador. Dedup + efecto en la misma tx.
 */
@Service
public class NotificationsHandler {

    public static final String CONSUMER = "eventcart-notifications";

    private final ProcessedEventGuard guard;
    private final NotificationJpaRepository notifications;
    private final EventDeserializer deserializer;
    private final Clock clock;

    public NotificationsHandler(ProcessedEventGuard guard, NotificationJpaRepository notifications,
                                EventDeserializer deserializer, Clock clock) {
        this.guard = guard;
        this.notifications = notifications;
        this.deserializer = deserializer;
        this.clock = clock;
    }

    @Transactional
    public void handle(ConsumedEvent event) {
        if (!guard.tryMarkProcessed(CONSUMER, UUID.fromString(event.eventId()))) {
            return;
        }
        switch (event.type()) {
            case "ReservationCreated" -> {
                ReservationCreatedPayload payload = deserializer.payload(event, ReservationCreatedPayload.class);
                save(payload.buyerId(), "ReservationCreated",
                        "Reserva creada: " + payload.quantity() + " unidad(es)");
            }
            case "OrderConfirmed" -> {
                OrderConfirmedPayload payload = deserializer.payload(event, OrderConfirmedPayload.class);
                save(payload.buyerId(), "OrderConfirmed",
                        "Pedido confirmado: " + payload.quantity() + " unidad(es)");
            }
            case "PaymentSimulationFailed" -> {
                PaymentSimulationFailedPayload payload =
                        deserializer.payload(event, PaymentSimulationFailedPayload.class);
                save(payload.buyerId(), "PaymentSimulationFailed", "Pago rechazado: " + payload.reason());
            }
            case "ReservationReleased" -> {
                ReservationReleasedPayload payload = deserializer.payload(event, ReservationReleasedPayload.class);
                save(payload.buyerId(), "ReservationReleased", "Reserva liberada");
            }
            case "ReservationExpired" -> {
                ReservationExpiredPayload payload = deserializer.payload(event, ReservationExpiredPayload.class);
                save(payload.buyerId(), "ReservationExpired",
                        "Reserva expirada: " + payload.quantity() + " unidad(es) liberada(s)");
            }
            default -> {
                // Tipo conocido sin notificación asociada: queda registrado como procesado.
            }
        }
    }

    private void save(UUID buyerId, String type, String message) {
        NotificationEntity notification = new NotificationEntity();
        notification.setId(UUID.randomUUID());
        notification.setBuyerId(buyerId);
        notification.setType(type);
        notification.setMessage(message);
        notification.setCreatedAt(clock.instant());
        notifications.save(notification);
    }
}
