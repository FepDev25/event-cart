package com.eventcart.web;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escribe una respuesta RFC 9457 ({@code application/problem+json}) fuera del flujo MVC
 * (entry point / access denied handler de Spring Security).
 */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static void write(HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String type,
                             String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", URI.create("urn:eventcart:error:" + type).toString());
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), body);
    }
}
