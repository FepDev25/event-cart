package com.eventcart.reservation.expiry;

import com.eventcart.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("worker")
@Import(TestcontainersConfiguration.class)
class WorkerProfileIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void workerProfileRunsExpirySchedulerAndSkipsPublisher() {
        assertNotNull(context.getBean(ReservationExpiryScheduler.class),
                "the expiry scheduler must be active in the worker profile");
        assertFalse(context.containsBean("outboxPublisher"),
                "the outbox publisher belongs to the API process, not the worker");
    }
}
