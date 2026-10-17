package com.eventcart.eventing.consumer;

import com.eventcart.eventing.contract.OrderConfirmedPayload;
import com.eventcart.eventing.projection.SalesProjectionJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Proyección de ventas: suma unidades confirmadas por lanzamiento (puede ir con retraso visible).
 */
@Service
public class SalesProjectionHandler {

    public static final String CONSUMER = "eventcart-sales";

    private final ProcessedEventGuard guard;
    private final SalesProjectionJpaRepository salesProjection;
    private final EventDeserializer deserializer;

    public SalesProjectionHandler(ProcessedEventGuard guard, SalesProjectionJpaRepository salesProjection,
                                  EventDeserializer deserializer) {
        this.guard = guard;
        this.salesProjection = salesProjection;
        this.deserializer = deserializer;
    }

    @Transactional
    public void handle(ConsumedEvent event) {
        if (!guard.tryMarkProcessed(CONSUMER, UUID.fromString(event.eventId()))) {
            return;
        }
        if ("OrderConfirmed".equals(event.type())) {
            OrderConfirmedPayload payload = deserializer.payload(event, OrderConfirmedPayload.class);
            salesProjection.addConfirmedUnits(payload.launchId(), payload.quantity());
        }
    }
}
