package com.eventcart.eventing.config;

import com.eventcart.eventing.contract.Topics;
import com.eventcart.eventing.metrics.EventingMetrics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Reintentos con backoff; al agotar intentos publica el evento en {@code <topic>.DLT}.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final long RETRY_INTERVAL_MS = 500L;
    private static final long MAX_ATTEMPTS = 3L;

    @Bean
    CommonErrorHandler commonErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                          EventingMetrics metrics) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    metrics.deadLettered();
                    return new TopicPartition(Topics.deadLetter(record.topic()), -1);
                });
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_ATTEMPTS));
    }
}
