package com.eventcart.identity.service;

import com.eventcart.shared.Hashes;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generación y hash de tokens de sesión opacos. Solo se persiste el hash.
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 32;

    private Tokens() {
    }

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static String hash(String token) {
        return Hashes.sha256(token);
    }
}
