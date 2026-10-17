package com.eventcart.reservation;

import com.eventcart.launch.Launch;

import java.time.Instant;
import java.util.UUID;

/**
 * Servicio de dominio que coordina el ciclo de vida de una reserva con el inventario del lanzamiento.
 *
 * <p>En M1 expresa las reglas sobre el modelo puro. La atomicidad real (una sola transacción
 * PostgreSQL con el {@code UPDATE} condicional sobre {@code launches}) se realiza en M2.
 */
public class ReservationService {

    /**
     * Reserva {@code quantity} unidades para {@code buyerId}.
     *
     * @param unitsAlreadyHeldByBuyer unidades activas del comprador en este lanzamiento (para el límite por comprador)
     */
    public Reservation reserve(Launch launch, UUID buyerId, int quantity, int unitsAlreadyHeldByBuyer, Instant now) {
        launch.assertReservable(now, quantity);

        Integer limit = launch.getPerBuyerLimit();
        if (limit != null && unitsAlreadyHeldByBuyer + quantity > limit) {
            throw new PurchaseLimitExceededException("buyer " + buyerId + " would hold "
                    + (unitsAlreadyHeldByBuyer + quantity) + " units, limit is " + limit
                    + " on launch " + launch.getId());
        }

        launch.reserveUnits(quantity, now);
        return Reservation.create(launch.getId(), buyerId, quantity, launch.getUnitPrice(), launch.getReservationTtl(), now);
    }

    public void confirm(Launch launch, Reservation reservation, Instant now) {
        reservation.confirm(now);
        launch.confirmUnits(reservation.getQuantity(), now);
    }

    public void release(Launch launch, Reservation reservation, Instant now) {
        reservation.release(now);
        launch.releaseUnits(reservation.getQuantity(), now);
    }

    public void expire(Launch launch, Reservation reservation, Instant now) {
        reservation.expire(now);
        launch.releaseUnits(reservation.getQuantity(), now);
    }
}
