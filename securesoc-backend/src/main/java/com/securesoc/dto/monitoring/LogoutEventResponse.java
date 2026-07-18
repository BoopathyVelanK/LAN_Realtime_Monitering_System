package com.securesoc.dto.monitoring;

import java.time.Instant;
import java.util.UUID;

/** Read shape for GET /monitoring/logout - mirrors LogoutEvent plus the
 * endpoint's hostname. */
public record LogoutEventResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    String osUsername,
    String sessionId,
    Instant logoutTime,
    Instant receivedAt
) {}
