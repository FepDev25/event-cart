package com.eventcart.web;

import com.eventcart.identity.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Aplica el rate limit básico a las operaciones sensibles (reserva y checkout). El filtro corre
 * después de la cadena de seguridad, por lo que el usuario autenticado ya está en el contexto.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final InMemoryRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(InMemoryRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isRateLimited(request) && !rateLimiter.tryAcquire(key(request))) {
            ProblemResponses.write(response, objectMapper, HttpStatus.TOO_MANY_REQUESTS, "rate-limited",
                    "Too many requests, please slow down");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isRateLimited(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.matches("/api/v1/launches/[^/]+/reservations")
                || path.matches("/api/v1/reservations/[^/]+/checkout");
    }

    private static String key(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.id();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
