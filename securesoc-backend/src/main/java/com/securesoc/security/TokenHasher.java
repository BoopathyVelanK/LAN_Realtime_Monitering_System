package com.securesoc.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Shared by refresh tokens and agent tokens: generate a random opaque
 * secret, hand the raw value to the client exactly once, and persist only
 * its SHA-256 hash. Neither token type is a JWT - both need to be
 * instantly revocable server-side, which a self-contained signed token
 * can't do before its own expiry.
 */
public final class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenHasher() {}

    public static String generateOpaqueToken() {
        byte[] bytes = new byte[32]; // 256 bits
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JDK - this is
            // unreachable in practice, but fail loudly rather than silently
            // if it somehow isn't.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
