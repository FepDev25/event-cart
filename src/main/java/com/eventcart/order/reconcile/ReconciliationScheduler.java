package com.eventcart.order.reconcile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara la reconciliación periódica. Solo en el proceso worker (perfil {@code worker}).
 */
@Component
@Profile("worker")
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliationService;

    public ReconciliationScheduler(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${eventcart.reconciliation.poll-interval:10s}")
    public void poll() {
        int failed = reconciliationService.reconcile();
        if (failed > 0) {
            log.info("Reconciliation resolved {} pending order(s)", failed);
        }
    }
}
