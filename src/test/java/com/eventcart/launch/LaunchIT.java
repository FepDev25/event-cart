package com.eventcart.launch;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LaunchIT {

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

    private String createBody(int stock) {
        Instant opens = Instant.now().minusSeconds(60);
        Instant closes = Instant.now().plusSeconds(3600);
        return """
                {"title":"Drop","description":"d","productName":"Tee","productRef":"SKU-1","unitPrice":25.00,"currency":"USD","totalStock":%d,"opensAt":"%s","closesAt":"%s","reservationTtlSeconds":600,"perBuyerLimit":2}"""
                .formatted(stock, opens, closes);
    }

    private String updateBody() {
        Instant opens = Instant.now().minusSeconds(60);
        Instant closes = Instant.now().plusSeconds(3600);
        return """
                {"title":"Drop v2","description":"d","productName":"Tee","productRef":"SKU-1","unitPrice":30.00,"currency":"USD","opensAt":"%s","closesAt":"%s","reservationTtlSeconds":600,"perBuyerLimit":2}"""
                .formatted(opens, closes);
    }

    private Map<String, Object> createLaunch(String token, int stock) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/launches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(stock)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result);
    }

    @Test
    void organizerCreatesAndOpensLaunch() throws Exception {
        String token = token("ORGANIZER");
        Map<String, Object> created = createLaunch(token, 10);
        assertEquals("DRAFT", created.get("status"));
        assertEquals(10, created.get("availableUnits"));
        String id = (String) created.get("id");

        mockMvc.perform(post("/api/v1/launches/" + id + "/schedule")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
        MvcResult opened = mockMvc.perform(post("/api/v1/launches/" + id + "/open")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("OPEN", json(opened).get("status"));

        mockMvc.perform(get("/api/v1/launches/" + id)).andExpect(status().isOk());
    }

    @Test
    void reconfigureAfterOpenConflicts() throws Exception {
        String token = token("ORGANIZER");
        String id = (String) createLaunch(token, 10).get("id");
        mockMvc.perform(post("/api/v1/launches/" + id + "/open")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());

        MvcResult result = mockMvc.perform(patch("/api/v1/launches/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody()))
                .andExpect(status().isConflict())
                .andReturn();
        String contentType = result.getResponse().getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.contains("problem+json"));
    }

    @Test
    void buyerCannotCreateLaunch() throws Exception {
        String token = token("BUYER");
        mockMvc.perform(post("/api/v1/launches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(10)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCreateIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/launches").contentType(MediaType.APPLICATION_JSON).content(createBody(10)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherOrganizerCannotModify() throws Exception {
        String owner = token("ORGANIZER");
        String other = token("ORGANIZER");
        String id = (String) createLaunch(owner, 10).get("id");

        mockMvc.perform(post("/api/v1/launches/" + id + "/open")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isForbidden());
    }

    @SuppressWarnings("unchecked")
    @Test
    void listIncludesCreatedLaunch() throws Exception {
        String token = token("ORGANIZER");
        String id = (String) createLaunch(token, 5).get("id");
        mockMvc.perform(post("/api/v1/launches/" + id + "/open")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());

        MvcResult list = mockMvc.perform(get("/api/v1/launches")).andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> launches =
                objectMapper.readValue(list.getResponse().getContentAsString(), List.class);
        assertTrue(launches.stream().anyMatch(item -> id.equals(item.get("id"))));
    }

    @Test
    void getUnknownLaunchIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/launches/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
