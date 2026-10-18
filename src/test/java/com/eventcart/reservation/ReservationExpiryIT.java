package com.eventcart.reservation;

import com.eventcart.TestcontainersConfiguration;
import com.eventcart.eventing.outbox.OutboxJpaRepository;
import com.eventcart.reservation.expiry.ReservationExpiryService;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReservationExpiryIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservationExpiryService expiryService;

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

    private String openLaunch(String organizerToken, int stock, int ttlSeconds, Integer perBuyerLimit)
            throws Exception {
        String limit = perBuyerLimit == null ? "null" : perBuyerLimit.toString();
        String body = """
                {"title":"Drop","description":"d","productName":"Tee","productRef":"SKU-1","unitPrice":25.00,"currency":"USD","totalStock":%d,"opensAt":"%s","closesAt":"%s","reservationTtlSeconds":%d,"perBuyerLimit":%s}"""
                .formatted(stock, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), ttlSeconds, limit);
        MvcResult created = mockMvc.perform(post("/api/v1/launches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String id = (String) json(created).get("id");
        mockMvc.perform(post("/api/v1/launches/" + id + "/open")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + organizerToken)).andExpect(status().isOk());
        return id;
    }

    private MvcResult reserve(String buyerToken, String launchId, int quantity, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(post("/api/v1/launches/" + launchId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"))
                .andReturn();
    }

    private int availableUnits(String launchId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/launches/" + launchId))
                .andExpect(status().isOk()).andReturn();
        return ((Number) json(result).get("availableUnits")).intValue();
    }

    private String reservationStatus(String buyerToken, String reservationId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk()).andReturn();
        return (String) json(result).get("status");
    }

    @Test
    void expiredReservationIsReleasedExactlyOnceAndEmitsEvent() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 5, 1, 2);

        String reservationId = (String) json(reserve(buyer, launchId, 2, "exp-1")).get("id");
        assertEquals(3, availableUnits(launchId));

        Thread.sleep(1200);

        assertEquals(1, expiryService.expireDueReservations(), "the due reservation must be swept once");
        assertEquals("EXPIRED", reservationStatus(buyer, reservationId));
        assertEquals(5, availableUnits(launchId));

        UUID aggregateId = UUID.fromString(reservationId);
        assertTrue(outbox.findAll().stream().anyMatch(row -> aggregateId.equals(row.getAggregateId())
                && "ReservationExpired".equals(row.getEventType())), "an outbox event must be recorded");
        assertTrue(meterRegistry.get(ReservationExpiryService.EXPIRED_METRIC).counter().count() >= 1.0);

        // Un segundo barrido no libera de nuevo el stock.
        assertEquals(0, expiryService.expireDueReservations());
        assertEquals(5, availableUnits(launchId));

        // El uso del comprador se liberó: puede volver a reservar hasta su límite.
        assertEquals(201, reserve(buyer, launchId, 2, "exp-1b").getResponse().getStatus());
        assertEquals(3, availableUnits(launchId));
    }

    @Test
    void reservationWithinTtlIsNotExpired() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 5, 600, null);

        String reservationId = (String) json(reserve(buyer, launchId, 1, "exp-2")).get("id");

        assertEquals(0, expiryService.expireDueReservations());
        assertEquals("RESERVED", reservationStatus(buyer, reservationId));
        assertEquals(4, availableUnits(launchId));
    }
}
