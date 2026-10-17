package com.eventcart.eventing;

import com.eventcart.TestcontainersConfiguration;
import com.eventcart.eventing.contract.Topics;
import com.eventcart.eventing.outbox.OutboxEntity;
import com.eventcart.eventing.outbox.OutboxJpaRepository;
import com.eventcart.eventing.outbox.OutboxPublisher;
import com.eventcart.eventing.projection.AuditLogJpaRepository;
import com.eventcart.eventing.projection.NotificationJpaRepository;
import com.eventcart.eventing.projection.SalesProjectionJpaRepository;
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
import org.springframework.kafka.core.KafkaTemplate;
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
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventConsumersIT {

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
    private AuditLogJpaRepository auditLog;
    @Autowired
    private NotificationJpaRepository notifications;
    @Autowired
    private SalesProjectionJpaRepository salesProjection;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
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

    private String me(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return (String) json(result).get("id");
    }

    private String openLaunch(String organizerToken, int stock) throws Exception {
        String body = """
                {"title":"Drop","description":"d","productName":"Tee","productRef":"SKU-1","unitPrice":25.00,"currency":"USD","totalStock":%d,"opensAt":"%s","closesAt":"%s","reservationTtlSeconds":600,"perBuyerLimit":null}"""
                .formatted(stock, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
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
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
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

    private OutboxEntity outboxRowFor(String aggregateId) {
        UUID id = UUID.fromString(aggregateId);
        return outbox.findAll().stream().filter(row -> id.equals(row.getAggregateId())).findFirst().orElseThrow();
    }

    private void send(String topic, String key, String value) {
        try {
            kafkaTemplate.send(topic, key, value).get();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void await(BooleanSupplier condition, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("condition not met within " + timeout);
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
    void reservationEventReachesAuditAndNotifications() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String buyerId = me(buyer);
        String launchId = openLaunch(organizer, 10);
        String reservationId = reserve(buyer, launchId, "cons-1");

        outboxPublisher.publishPending();

        UUID aggregateId = UUID.fromString(reservationId);
        await(() -> auditLog.findAll().stream().anyMatch(row ->
                        aggregateId.equals(row.getAggregateId()) && "ReservationCreated".equals(row.getEventType())),
                Duration.ofSeconds(20));
        await(() -> notifications.countByBuyerIdAndType(UUID.fromString(buyerId), "ReservationCreated") >= 1,
                Duration.ofSeconds(20));
    }

    @Test
    void orderConfirmedUpdatesSalesProjection() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = reserve(buyer, launchId, "cons-2");
        checkout(buyer, reservationId, "APPROVE", "cons-2c");

        outboxPublisher.publishPending();

        UUID launchUuid = UUID.fromString(launchId);
        await(() -> salesProjection.findById(launchUuid).map(p -> p.getConfirmedUnits() >= 2).orElse(false),
                Duration.ofSeconds(20));
    }

    @Test
    void duplicateDeliveryIsProcessedOnce() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = reserve(buyer, launchId, "cons-3");

        outboxPublisher.publishPending();
        UUID aggregateId = UUID.fromString(reservationId);
        await(() -> auditLog.findAll().stream().anyMatch(row -> aggregateId.equals(row.getAggregateId())),
                Duration.ofSeconds(20));

        OutboxEntity row = outboxRowFor(reservationId);
        String eventId = (String) objectMapper.readValue(row.getPayload(), Map.class).get("eventId");

        // Redelivery: el mismo evento se reenvía; el consumidor debe deduplicarlo.
        send(Topics.RESERVATION, reservationId, row.getPayload());
        send(Topics.RESERVATION, reservationId, row.getPayload());

        Thread.sleep(2000);
        assertEquals(1, auditLog.countByEventId(UUID.fromString(eventId)));
    }

    @Test
    void unknownEventGoesToDeadLetterTopic() throws Exception {
        String key = UUID.randomUUID().toString();
        String bogus = """
                {"eventId":"%s","type":"Bogus","version":1,"aggregateId":"%s","occurredAt":"%s","payload":{}}"""
                .formatted(UUID.randomUUID(), key, Instant.now());

        send(Topics.RESERVATION, key, bogus);

        Optional<String> deadLetter = pollForKey(Topics.deadLetter(Topics.RESERVATION), key, Duration.ofSeconds(25));
        assertTrue(deadLetter.isPresent(), "expected the unsupported event in the dead-letter topic");
        assertTrue(deadLetter.get().contains("\"type\":\"Bogus\""));
    }
}
