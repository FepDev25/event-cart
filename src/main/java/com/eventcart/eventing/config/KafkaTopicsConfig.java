package com.eventcart.eventing.config;

import com.eventcart.eventing.contract.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Crea los topics por dominio y sus DLT al arrancar (vía KafkaAdmin).
 */
@Configuration
public class KafkaTopicsConfig {

    private static final int PARTITIONS = 3;
    private static final int REPLICAS = 1;

    @Bean
    NewTopic reservationTopic() {
        return TopicBuilder.name(Topics.RESERVATION).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    NewTopic orderTopic() {
        return TopicBuilder.name(Topics.ORDER).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    NewTopic reservationDeadLetterTopic() {
        return TopicBuilder.name(Topics.deadLetter(Topics.RESERVATION)).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    NewTopic orderDeadLetterTopic() {
        return TopicBuilder.name(Topics.deadLetter(Topics.ORDER)).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
