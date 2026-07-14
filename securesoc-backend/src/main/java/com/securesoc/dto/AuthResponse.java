package com.securesoc.dto;

import java.util.List;
import java.util.UUID;

/**
 * Field names and shape are a deliberate 1:1 mirror of the frontend's
 * AuthResponse interface (frontend/src/types/api.ts) - do not rename
 * fields here without updating that file too.
 */
public record AuthResponse(
    UUID userId,
    String username,
    String fullName,
    List<String> roles,
    String accessToken,
    String refreshToken,
    long accessTokenExpiresInSeconds
) {}
