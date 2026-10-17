package com.eventcart.reservation;

import com.eventcart.TestcontainersConfiguration;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReservationIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
                .andExpect(status().isOk())
                .andReturn();
        return (String) json(login).get("token");
    }

    private String openLaunch(String organizerToken, int stock, Integer perBuyerLimit) throws Exception {
        String limit = perBuyerLimit == null ? "null" : perBuyerLimit.toString();
        String body = """
                {"title":"Drop","description":"d","productName":"Tee","productRef":"SKU-1","unitPrice":25.00,"currency":"USD","totalStock":%d,"opensAt":"%s","closesAt":"%s","reservationTtlSeconds":600,"perBuyerLimit":%s}"""
                .formatted(stock, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), limit);
        MvcResult created = mockMvc.perform(post("/api/v1/launches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
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

    @Test
    void reserveDecreasesAvailability() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10, null);

        MvcResult result = reserve(buyer, launchId, 3, "k-1");
        assertEquals(201, result.getResponse().getStatus());
        assertEquals("RESERVED", json(result).get("status"));
        assertEquals(7, availableUnits(launchId));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10, null);

        mockMvc.perform(post("/api/v1/launches/" + launchId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void idempotentRetryReturnsSameReservation() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10, null);

        String firstId = (String) json(reserve(buyer, launchId, 2, "same-key")).get("id");
        MvcResult retry = reserve(buyer, launchId, 2, "same-key");

        assertEquals(201, retry.getResponse().getStatus());
        assertEquals(firstId, json(retry).get("id"));
        assertEquals(8, availableUnits(launchId));
    }

    @Test
    void idempotencyKeyReusedWithDifferentBodyConflicts() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10, null);

        assertEquals(201, reserve(buyer, launchId, 2, "k-conflict").getResponse().getStatus());
        assertEquals(409, reserve(buyer, launchId, 3, "k-conflict").getResponse().getStatus());
    }

    @Test
    void perBuyerLimitIsEnforced() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10, 2);

        assertEquals(201, reserve(buyer, launchId, 2, "limit-1").getResponse().getStatus());
        assertEquals(409, reserve(buyer, launchId, 1, "limit-2").getResponse().getStatus());
        assertEquals(8, availableUnits(launchId));
    }

    @Test
    void cancelReleasesStock() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10, null);

        String reservationId = (String) json(reserve(buyer, launchId, 3, "cancel-1")).get("id");
        assertEquals(7, availableUnits(launchId));

        MvcResult cancelled = mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyer))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("RELEASED", json(cancelled).get("status"));
        assertEquals(10, availableUnits(launchId));
    }

    @Test
    void anotherBuyerCannotCancel() throws Exception {
        String organizer = token("ORGANIZER");
        String buyerA = token("BUYER");
        String buyerB = token("BUYER");
        String launchId = openLaunch(organizer, 10, null);

        String reservationId = (String) json(reserve(buyerA, launchId, 1, "iso-1")).get("id");
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void noOversellUnderConcurrency() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 5, null);

        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            String key = "race-" + i;
            futures.add(pool.submit(() -> {
                start.await();
                return reserve(buyer, launchId, 1, key).getResponse().getStatus();
            }));
        }
        start.countDown();

        int created = 0;
        int rejected = 0;
        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 201) {
                created++;
            } else if (status == 409) {
                rejected++;
            }
        }
        pool.shutdown();

        assertEquals(5, created, "exactly the available stock must be reserved");
        assertEquals(15, rejected);
        assertEquals(0, availableUnits(launchId));
        assertNotNull(json(reserve(buyer, launchId, 1, "race-after")).get("status"));
    }
}
