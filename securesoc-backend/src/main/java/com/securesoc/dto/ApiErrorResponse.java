package com.securesoc.dto;

import java.time.Instant;

/** Mirrors the frontend's ApiError interface (frontend/src/types/api.ts). */
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path
) {}
