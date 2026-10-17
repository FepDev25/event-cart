package com.eventcart.web;

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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitIT {

    private static final String PASSWORD = "s3cret-pass";
    private static final int CAPACITY = 100;

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

    private String openLaunch(String organizerToken) throws Exception {
        String body = """
                {"title":"Drop","description":"d","productName":"Tee","productRef":"SKU-1","unitPrice":25.00,"currency":"USD","totalStock":1000,"opensAt":"%s","closesAt":"%s","reservationTtlSeconds":600,"perBuyerLimit":null}"""
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

    private int reserveStatus(String buyerToken, String launchId, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/launches/" + launchId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void exceedingCapacityIsRateLimited() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer);

        int lastAllowed = 0;
        for (int i = 1; i <= CAPACITY; i++) {
            lastAllowed = reserveStatus(buyer, launchId, "rl-" + i);
        }
        assertNotEquals(429, lastAllowed, "requests up to capacity must not be rate limited");

        assertEquals(429, reserveStatus(buyer, launchId, "rl-over"));
    }

    @Test
    void otherUserHasIndependentBudget() throws Exception {
        String organizer = token("ORGANIZER");
        String buyerA = token("BUYER");
        String buyerB = token("BUYER");
        String launchId = openLaunch(organizer);

        for (int i = 1; i <= CAPACITY; i++) {
            reserveStatus(buyerA, launchId, "a-" + i);
        }
        assertEquals(429, reserveStatus(buyerA, launchId, "a-over"));

        assertNotEquals(429, reserveStatus(buyerB, launchId, "b-1"));
    }
}
