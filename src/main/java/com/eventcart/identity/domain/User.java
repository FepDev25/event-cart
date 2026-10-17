package com.eventcart.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Usuario del prototipo: email + password (hash) y un rol. Identidad mínima para validar límites
 * y peticiones repetidas sin confiar en identidad suministrada por el cliente.
 */
public record User(UUID id, String email, String passwordHash, String displayName, Role role, Instant createdAt) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
