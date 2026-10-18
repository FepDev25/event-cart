package com.eventcart.eventing.outbox;

import com.eventcart.config.EventingProperties;
import com.eventcart.eventing.contract.EventType;
import com.eventcart.eventing.contract.UnsupportedEventException;
import com.eventcart.eventing.metrics.EventingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Publica las filas pendientes del outbox a Kafka y marca {@code published_at}. Un solo hilo de
 * scheduler ⇒ sin solapamiento; las filas sin publicar se reintentan (recuperación tras reinicio).
 */
@Component
@Profile("!worker")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxJpaRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventingProperties properties;
    private final Clock clock;
    private final EventingMetrics metrics;

    public OutboxPublisher(OutboxJpaRepository outbox, KafkaTemplate<String, String> kafkaTemplate,
                           EventingProperties properties, Clock clock, EventingMetrics metrics) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${eventcart.outbox.poll-interval:1s}")
    public void publishPending() {
        List<OutboxEntity> batch = outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(
                PageRequest.of(0, properties.batchSize()));
        for (OutboxEntity row : batch) {
            publish(row);
        }
    }

    private void publish(OutboxEntity row) {
        String topic = topicFor(row.getEventType());
        try {
            kafkaTemplate.send(topic, row.getAggregateId().toString(), row.getPayload()).get();
            row.setPublishedAt(clock.instant());
            row.setAttempts(row.getAttempts() + 1);
            outbox.save(row);
            metrics.outboxPublished();
            log.debug("Outbox published id={} type={} topic={}", row.getId(), row.getEventType(), topic);
        } catch (Exception ex) {
            row.setAttempts(row.getAttempts() + 1);
            outbox.save(row);
            metrics.outboxPublishFailed();
            log.warn("Outbox publish failed id={} type={} attempts={}", row.getId(), row.getEventType(),
                    row.getAttempts(), ex);
        }
    }

    private String topicFor(String eventType) {
        return EventType.fromWireName(eventType)
                .map(EventType::topic)
                .orElseThrow(() -> new UnsupportedEventException("no topic for event type: " + eventType));
    }
}
