package com.securesoc.security;

import com.securesoc.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Short-lived JWT access tokens only. Refresh tokens are a separate,
 * opaque, DB-backed concept (see RefreshToken entity / AuthService) -
 * deliberately NOT JWTs, so they can be revoked server-side instantly
 * (a self-contained JWT can't be revoked before it expires).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // HS256 requires a key of at least 256 bits; fail fast at startup
        // with a clear message rather than a cryptic error on first login.
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                "securesoc.jwt.secret must be at least 32 bytes (256 bits) for HS256. "
                    + "Set the JWT_SECRET environment variable to a longer random value.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(UUID userId, String username, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(properties.accessTokenTtlSeconds())))
            .signWith(key)
            .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return properties.accessTokenTtlSeconds();
    }

    public long getRefreshTokenTtlSeconds() {
        return properties.refreshTokenTtlSeconds();
    }

    /** Throws io.jsonwebtoken.JwtException (expired/malformed/bad signature)
     * on any invalid token - callers (JwtAuthenticationFilter) catch it and
     * treat the request as unauthenticated rather than erroring. */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
