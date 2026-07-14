package com.securesoc.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenHasherTest {

    @Test
    void generateOpaqueToken_producesNonEmptyUrlSafeValue() {
        String token = TokenHasher.generateOpaqueToken();
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertFalse(token.contains("+"));
        assertFalse(token.contains("/"));
    }

    @Test
    void generateOpaqueToken_isRandomEachCall() {
        String a = TokenHasher.generateOpaqueToken();
        String b = TokenHasher.generateOpaqueToken();
        assertNotEquals(a, b);
    }

    @Test
    void sha256Hex_isDeterministicAndCorrectLength() {
        String hash1 = TokenHasher.sha256Hex("hello-world");
        String hash2 = TokenHasher.sha256Hex("hello-world");
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // 32 bytes as hex
    }

    @Test
    void sha256Hex_differsForDifferentInput() {
        assertNotEquals(TokenHasher.sha256Hex("a"), TokenHasher.sha256Hex("b"));
    }
}
