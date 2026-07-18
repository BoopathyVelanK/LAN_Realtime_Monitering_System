package com.securesoc.dto.monitoring;

import java.time.Instant;
import java.util.UUID;

/** Read shape for GET /monitoring/idle - mirrors IdleEvent plus the
 * endpoint's hostname. */
public record IdleEventResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    Integer idleSeconds,
    Instant recordedAt
) {}
