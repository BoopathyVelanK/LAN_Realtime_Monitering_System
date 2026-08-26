package com.securesoc.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Field names and shape are a deliberate 1:1 mirror of the frontend's
 * RiskScoreResponse interface (frontend/src/types/api.ts) - that interface
 * predates this DTO (contract-first) and was already committed, unused,
 * pending this checkpoint. {@code level} is serialized as the enum's
 * {@code name()} (SAFE/LOW/MEDIUM/HIGH/CRITICAL), matching how every other
 * enum-bearing DTO in this codebase (e.g. EndpointSummaryResponse.status)
 * crosses the wire as a plain string.
 */
public record RiskScoreResponse(
    UUID endpointId,
    short score,
    String level,
    Instant updatedAt
) {}
