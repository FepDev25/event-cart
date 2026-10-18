package com.eventcart.reservation.expiry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara la expiración periódica. Solo se activa en el proceso worker (perfil {@code worker}), de
 * modo que la API no compite por el barrido de reservas vencidas.
 */
@Component
@Profile("worker")
public class ReservationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final ReservationExpiryService expiryService;

    public ReservationExpiryScheduler(ReservationExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    @Scheduled(fixedDelayString = "${eventcart.expiry.poll-interval:5s}")
    public void poll() {
        int expired = expiryService.expireDueReservations();
        if (expired > 0) {
            log.info("Expiry sweep released {} reservation(s)", expired);
        }
    }
}
