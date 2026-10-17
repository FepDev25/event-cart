package com.eventcart.identity.web;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
}
