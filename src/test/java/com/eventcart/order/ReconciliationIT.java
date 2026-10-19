package com.eventcart.order;

import com.eventcart.TestcontainersConfiguration;
import com.eventcart.eventing.outbox.OutboxJpaRepository;
import com.eventcart.order.reconcile.ReconciliationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "eventcart.reconciliation.pending-timeout=1s")
@Import(TestcontainersConfiguration.class)
class ReconciliationIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private OutboxJpaRepository outbox;

    @Autowired
    private MeterRegistry meterRegistry;

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

    private String reserve(String buyerToken, String launchId, int quantity, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/launches/" + launchId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated()).andReturn();
        return (String) json(result).get("id");
    }

    private String checkout(String buyerToken, String reservationId, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentToken\":\"TIMEOUT\"}"))
                .andExpect(status().isAccepted()).andReturn();
        return (String) json(result).get("id");
    }

    private int availableUnits(String launchId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/launches/" + launchId))
                .andExpect(status().isOk()).andReturn();
        return ((Number) json(result).get("availableUnits")).intValue();
    }

    private String orderStatus(String buyerToken, String orderId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk()).andReturn();
        return (String) json(result).get("status");
    }

    private String reservationStatus(String buyerToken, String reservationId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk()).andReturn();
        return (String) json(result).get("status");
    }

    @Test
    void stalePendingOrderIsFailedAndReservationReleasedOnce() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 5);

        String reservationId = reserve(buyer, launchId, 2, "rec-1");
        String orderId = checkout(buyer, reservationId, "rec-1c");
        assertEquals(3, availableUnits(launchId));
        assertEquals("PENDING", orderStatus(buyer, orderId));

        Thread.sleep(1200);

        assertEquals(1, reconciliationService.reconcile(), "the stale pending order must be resolved");
        assertEquals("FAILED", orderStatus(buyer, orderId));
        assertEquals("RELEASED", reservationStatus(buyer, reservationId));
        assertEquals(5, availableUnits(launchId));

        UUID aggregateId = UUID.fromString(orderId);
        assertTrue(outbox.findAll().stream().anyMatch(row -> aggregateId.equals(row.getAggregateId())
                && "PaymentSimulationFailed".equals(row.getEventType())), "a failure event must be recorded");
        assertTrue(meterRegistry.get(ReconciliationService.FAILED_ORDERS_METRIC).counter().count() >= 1.0);

        // Segunda pasada: nada que reconciliar, no se libera stock de nuevo.
        assertEquals(0, reconciliationService.reconcile());
        assertEquals(5, availableUnits(launchId));
    }
}
