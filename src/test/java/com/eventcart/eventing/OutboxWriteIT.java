package com.eventcart.eventing;

import com.eventcart.TestcontainersConfiguration;
import com.eventcart.eventing.outbox.OutboxEntity;
import com.eventcart.eventing.outbox.OutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OutboxWriteIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxJpaRepository outbox;

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

    private MvcResult reserve(String buyerToken, String launchId, int quantity, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/launches/" + launchId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"))
                .andReturn();
    }

    private MvcResult checkout(String buyerToken, String reservationId, String paymentToken, String key)
            throws Exception {
        return mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"" + paymentToken + "\"}"))
                .andReturn();
    }

    private List<OutboxEntity> rowsFor(String aggregateId) {
        UUID id = UUID.fromString(aggregateId);
        return outbox.findAll().stream().filter(row -> id.equals(row.getAggregateId())).toList();
    }

    @Test
    void reserveRecordsReservationCreatedInSameTransaction() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);

        String reservationId = (String) json(reserve(buyer, launchId, 2, "ob-1")).get("id");

        List<OutboxEntity> rows = rowsFor(reservationId);
        assertEquals(1, rows.size());
        assertEquals("ReservationCreated", rows.get(0).getEventType());
        assertTrue(rows.get(0).getPayload().contains("\"type\":\"ReservationCreated\""));
        assertTrue(rows.get(0).getPayload().contains(reservationId));
    }

    @Test
    void rejectedReserveLeavesNoOutboxRow() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 0);

        assertEquals(409, reserve(buyer, launchId, 1, "ob-2").getResponse().getStatus());
        assertTrue(outbox.findAll().stream().noneMatch(row -> row.getPayload().contains(launchId)));
    }

    @Test
    void cancelRecordsReservationReleased() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = (String) json(reserve(buyer, launchId, 1, "ob-3")).get("id");

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyer)).andExpect(status().isOk());

        List<String> types = rowsFor(reservationId).stream().map(OutboxEntity::getEventType).toList();
        assertTrue(types.contains("ReservationCreated"));
        assertTrue(types.contains("ReservationReleased"));
    }

    @Test
    void approvedCheckoutRecordsOrderConfirmed() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = (String) json(reserve(buyer, launchId, 1, "ob-4")).get("id");

        String orderId = (String) json(checkout(buyer, reservationId, "APPROVE", "ob-4c")).get("id");

        List<OutboxEntity> rows = rowsFor(orderId);
        assertEquals(1, rows.size());
        assertEquals("OrderConfirmed", rows.get(0).getEventType());
    }

    @Test
    void declinedCheckoutRecordsPaymentSimulationFailed() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = (String) json(reserve(buyer, launchId, 1, "ob-5")).get("id");

        String orderId = (String) json(checkout(buyer, reservationId, "DECLINE", "ob-5c")).get("id");

        List<OutboxEntity> rows = rowsFor(orderId);
        assertEquals(1, rows.size());
        assertEquals("PaymentSimulationFailed", rows.get(0).getEventType());
    }
}
