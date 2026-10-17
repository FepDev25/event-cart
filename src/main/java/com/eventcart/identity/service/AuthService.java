package com.eventcart.identity.service;

import com.eventcart.config.AuthProperties;
import com.eventcart.identity.EmailAlreadyUsedException;
import com.eventcart.identity.InvalidCredentialsException;
import com.eventcart.identity.domain.Role;
import com.eventcart.identity.domain.User;
import com.eventcart.identity.persistence.IdentityMapper;
import com.eventcart.identity.persistence.SessionEntity;
import com.eventcart.identity.persistence.SessionJpaRepository;
import com.eventcart.identity.persistence.UserEntity;
import com.eventcart.identity.persistence.UserJpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserJpaRepository users;
    private final SessionJpaRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final IdentityMapper mapper;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthService(UserJpaRepository users, SessionJpaRepository sessions, PasswordEncoder passwordEncoder,
                       IdentityMapper mapper, AuthProperties properties, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public User register(String email, String rawPassword, String displayName, Role role) {
        String normalizedEmail = normalizeEmail(email);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }
        Instant now = clock.instant();
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setEmail(normalizedEmail);
        entity.setPasswordHash(passwordEncoder.encode(rawPassword));
        entity.setDisplayName(displayName);
        entity.setRole(role == null ? Role.BUYER : role);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        users.save(entity);
        return mapper.toDomain(entity);
    }

    @Transactional
    public LoginResult login(String email, String rawPassword) {
        UserEntity user = users.findByEmail(normalizeEmail(email))
                .filter(candidate -> passwordEncoder.matches(rawPassword, candidate.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("invalid email or password"));

        String token = Tokens.generate();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.sessionTtl());

        SessionEntity session = new SessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setTokenHash(Tokens.hash(token));
        session.setExpiresAt(expiresAt);
        session.setCreatedAt(now);
        sessions.save(session);

        return new LoginResult(token, expiresAt, mapper.toDomain(user));
    }

    @Transactional(readOnly = true)
    public Optional<User> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.findByTokenHash(Tokens.hash(token))
                .filter(session -> session.getRevokedAt() == null && now.isBefore(session.getExpiresAt()))
                .flatMap(session -> users.findById(session.getUserId()))
                .map(mapper::toDomain);
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessions.findByTokenHash(Tokens.hash(token)).ifPresent(session -> {
            session.setRevokedAt(clock.instant());
            sessions.save(session);
        });
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
