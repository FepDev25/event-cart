package com.eventcart.checkout;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CheckoutIT {

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

    private MvcResult checkout(String buyerToken, String reservationId, String paymentToken, String key)
            throws Exception {
        return mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"" + paymentToken + "\"}"))
                .andReturn();
    }

    private Map<String, Object> launch(String launchId) throws Exception {
        return json(mockMvc.perform(get("/api/v1/launches/" + launchId)).andExpect(status().isOk()).andReturn());
    }

    private Map<String, Object> reservation(String buyerToken, String reservationId) throws Exception {
        return json(mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)).andExpect(status().isOk()).andReturn());
    }

    @Test
    void approvedCheckoutConfirmsOrderAndMovesStock() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = reserve(buyer, launchId, 3, "r-1");

        MvcResult result = checkout(buyer, reservationId, "APPROVE", "c-1");
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("CONFIRMED", json(result).get("status"));
        assertEquals("APPROVED", json(result).get("paymentResult"));

        Map<String, Object> launch = launch(launchId);
        assertEquals(7, launch.get("availableUnits"));
        assertEquals(0, launch.get("reservedUnits"));
        assertEquals(3, launch.get("confirmedUnits"));
        assertEquals("CONFIRMED", reservation(buyer, reservationId).get("status"));
    }

    @Test
    void declinedCheckoutFailsOrderAndReleasesStock() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = reserve(buyer, launchId, 3, "r-2");

        MvcResult result = checkout(buyer, reservationId, "DECLINE", "c-2");
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("FAILED", json(result).get("status"));

        Map<String, Object> launch = launch(launchId);
        assertEquals(10, launch.get("availableUnits"));
        assertEquals(0, launch.get("reservedUnits"));
        assertEquals("RELEASED", reservation(buyer, reservationId).get("status"));
    }

    @Test
    void timeoutCheckoutLeavesOrderPending() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = reserve(buyer, launchId, 1, "r-3");

        MvcResult result = checkout(buyer, reservationId, "TIMEOUT", "c-3");
        assertEquals(202, result.getResponse().getStatus());
        assertEquals("PENDING", json(result).get("status"));

        assertEquals("RESERVED", reservation(buyer, reservationId).get("status"));
        assertEquals(9, launch(launchId).get("availableUnits"));
    }

    @Test
    void checkoutRequiresIdempotencyKey() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = reserve(buyer, launchId, 1, "r-4");

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentToken\":\"APPROVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutIsIdempotent() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = reserve(buyer, launchId, 2, "r-5");

        String firstOrderId = (String) json(checkout(buyer, reservationId, "APPROVE", "c-5")).get("id");
        MvcResult retry = checkout(buyer, reservationId, "APPROVE", "c-5");
        assertEquals(200, retry.getResponse().getStatus());
        assertEquals(firstOrderId, json(retry).get("id"));

        Map<String, Object> launch = launch(launchId);
        assertEquals(8, launch.get("availableUnits"));
        assertEquals(2, launch.get("confirmedUnits"));
    }

    @Test
    void unknownPaymentTokenIsRejected() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = reserve(buyer, launchId, 1, "r-6");

        assertEquals(400, checkout(buyer, reservationId, "NOPE", "c-6").getResponse().getStatus());
    }

    @Test
    void anotherBuyerCannotCheckout() throws Exception {
        String organizer = token("ORGANIZER");
        String buyerA = token("BUYER");
        String buyerB = token("BUYER");
        String launchId = openLaunch(organizer, 10);
        String reservationId = reserve(buyerA, launchId, 1, "r-7");

        assertEquals(404, checkout(buyerB, reservationId, "APPROVE", "c-7").getResponse().getStatus());
        assertNotNull(reservation(buyerA, reservationId).get("status"));
    }
}
