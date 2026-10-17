package com.eventcart.identity.web;

import com.eventcart.identity.domain.Role;

import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, Role role) {
}
