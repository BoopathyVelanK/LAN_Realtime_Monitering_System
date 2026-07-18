package com.securesoc.dto.monitoring;

import java.time.Instant;
import java.util.UUID;

/** Read shape for GET /monitoring/login - mirrors LoginEvent plus the
 * endpoint's hostname (joined in, so the frontend doesn't need a second
 * lookup against /endpoints to display who an event belongs to). */
public record LoginEventResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    String osUsername,
    String sessionId,
    Instant loginTime,
    Instant receivedAt
) {}
