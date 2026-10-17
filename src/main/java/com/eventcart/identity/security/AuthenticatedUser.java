package com.eventcart.identity.security;

import com.eventcart.identity.domain.Role;

import java.util.UUID;

/**
 * Principal autenticado disponible en los controladores.
 */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
