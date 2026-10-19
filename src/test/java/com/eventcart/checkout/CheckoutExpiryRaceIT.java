package com.eventcart.checkout;

import com.eventcart.TestcontainersConfiguration;
import com.eventcart.reservation.expiry.ReservationExpiryService;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "eventcart.checkout.latency=2s",
        "eventcart.checkout.timeout=5s",
        "eventcart.expiry.grace-window=1s"
})
@Import(TestcontainersConfiguration.class)
class CheckoutExpiryRaceIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservationExpiryService expiryService;

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

    private String openLaunch(String organizerToken, int stock, int ttlSeconds) throws Exception {
        String body = """
                {"title":"Drop","description":"d","productName":"Tee","productRef":"SKU-1","unitPrice":25.00,"currency":"USD","totalStock":%d,"opensAt":"%s","closesAt":"%s","reservationTtlSeconds":%d,"perBuyerLimit":null}"""
                .formatted(stock, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), ttlSeconds);
        MvcResult created = mockMvc.perform(post("/api/v1/launches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String id = (String) json(created).get("id");
        mockMvc.perform(post("/api/v1/launches/" + id + "/open")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + organizerToken)).andExpect(status().isOk());
        return id;
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
    void approvedCheckoutRacingExpiryFailsWithoutReReleasingStock() throws Exception {
        String organizer = token("ORGANIZER");
        String buyer = token("BUYER");
        String launchId = openLaunch(organizer, 5, 1);

        MvcResult reservation = mockMvc.perform(post("/api/v1/launches/" + launchId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyer)
                        .header("Idempotency-Key", "race-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isCreated()).andReturn();
        String reservationId = (String) json(reservation).get("id");
        assertEquals(3, availableUnits(launchId));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<MvcResult> checkout = pool.submit(() -> mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyer)
                        .header("Idempotency-Key", "race-1c")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentToken\":\"APPROVE\"}"))
                .andReturn());

        // La reserva vence y el checkout (2s) sigue en vuelo; fuera de la ventana de gracia (1s).
        Thread.sleep(1500);
        assertEquals(1, expiryService.expireDueReservations(), "the sweep must expire the reservation");
        assertEquals("EXPIRED", reservationStatus(buyer, reservationId));
        assertEquals(5, availableUnits(launchId));

        MvcResult result = checkout.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(200, result.getResponse().getStatus());
        // El pago aprobó pero la reserva ya expiró: el pedido queda FAILED y el stock no se libera de nuevo.
        assertEquals("FAILED", json(result).get("status"));
        assertEquals("EXPIRED", reservationStatus(buyer, reservationId));
        assertEquals(5, availableUnits(launchId));
    }
}
