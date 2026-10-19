package com.eventcart.eventing;

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
@ActiveProfiles("consumer")
@Import(TestcontainersConfiguration.class)
class ConsumerProfileIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void consumerProfileRunsConsumersOnlyWithoutPublisher() {
        assertNotNull(context.getBean("notificationsConsumer"));
        assertNotNull(context.getBean("salesProjectionConsumer"));
        assertNotNull(context.getBean("auditConsumer"));
        assertFalse(context.containsBean("outboxPublisher"),
                "the outbox publisher runs in the API process, not the consumer process");
    }
}
