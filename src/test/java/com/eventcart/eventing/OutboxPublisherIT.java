package com.eventcart.eventing;

import com.eventcart.TestcontainersConfiguration;
import com.eventcart.eventing.contract.Topics;
import com.eventcart.eventing.outbox.OutboxEntity;
import com.eventcart.eventing.outbox.OutboxJpaRepository;
import com.eventcart.eventing.outbox.OutboxPublisher;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OutboxPublisherIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxJpaRepository outbox;

    @Autowired
    private KafkaContainer kafkaContainer;

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private String token(String role) throws Exception {
        String email = "u-" + UUID.randomUUID() + "@eventcart.test";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"U","role":"%s"}"""
                                .formatted(email, PASSWORD, role)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/session").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return (String) json(login).get("token");
    }

    private String openLaunch(String organizerToken) throws Exception {
        String body = """
                {"title":"Drop","description":"d","productName":"Tee","productRef":"SKU-1","unitPrice":25.00,"currency":"USD","totalStock":10,"opensAt":"%s","closesAt":"%s","reservationTtlSeconds":600,"perBuyerLimit":null}"""
                .formatted(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        MvcResult created = mockMvc.perform(post("/api/v1/launches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String id = (String) json(created).get("id");
        mockMvc.perform(post("/api/v1/launches/" + id + "/open")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + organizerToken)).andExpect(status().isOk());
        return id;
    }

    private String reserve(String buyerToken, String launchId, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/launches/" + launchId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isCreated()).andReturn();
        return (String) json(result).get("id");
    }

    private String checkout(String buyerToken, String reservationId, String paymentToken, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"" + paymentToken + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (String) json(result).get("id");
    }

    private Optional<String> pollForKey(String topic, String key, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return Optional.of(record.value());
                    }
                }
            }
            return Optional.empty();
        }
    }

    @Test
    void publishesReservationCreatedToKafkaAndMarksOutbox() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer);
        String reservationId = reserve(buyer, launchId, "pub-1");

        outboxPublisher.publishPending();

        Optional<String> value = pollForKey(Topics.RESERVATION, reservationId, Duration.ofSeconds(15));
        assertTrue(value.isPresent(), "expected ReservationCreated message on the reservation topic");
        assertTrue(value.get().contains("\"type\":\"ReservationCreated\""));

        UUID aggregateId = UUID.fromString(reservationId);
        List<OutboxEntity> rows = outbox.findAll().stream()
                .filter(row -> aggregateId.equals(row.getAggregateId())).toList();
        assertTrue(rows.stream().allMatch(row -> row.getPublishedAt() != null));
    }

    @Test
    void publishesOrderConfirmedToKafka() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer);
        String reservationId = reserve(buyer, launchId, "pub-2");
        String orderId = checkout(buyer, reservationId, "APPROVE", "pub-2c");

        outboxPublisher.publishPending();

        Optional<String> value = pollForKey(Topics.ORDER, orderId, Duration.ofSeconds(15));
        assertTrue(value.isPresent(), "expected OrderConfirmed message on the order topic");
        assertTrue(value.get().contains("\"type\":\"OrderConfirmed\""));
    }
}
