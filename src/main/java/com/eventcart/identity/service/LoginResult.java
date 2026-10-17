package com.eventcart.identity.service;

import com.eventcart.identity.domain.User;

import java.time.Instant;

public record LoginResult(String token, Instant expiresAt, User user) {
}
