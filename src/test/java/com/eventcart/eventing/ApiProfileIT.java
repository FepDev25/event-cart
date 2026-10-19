package com.eventcart.eventing;

import com.eventcart.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("api")
@Import(TestcontainersConfiguration.class)
class ApiProfileIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void apiProfileRunsPublisherWithoutConsumers() {
        assertTrue(context.containsBean("outboxPublisher"),
                "the outbox publisher must be active in the api process");
        assertFalse(context.containsBean("notificationsConsumer"),
                "consumers run in the dedicated consumer process, not the api");
    }
}
