package com.securesoc.dto.monitoring;

import java.time.Instant;
import java.util.UUID;

/** Read shape for GET /monitoring/network-usage - mirrors
 * NetworkUsageEvent plus the endpoint's hostname. sampledAt is the
 * agent's collection time (may be null for rows recorded before this
 * field existed, or from a not-yet-upgraded agent); recordedAt remains
 * the backend's own ingestion time, unchanged. */
public record NetworkUsageEventResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    Long bytesSent,
    Long bytesReceived,
    String interfaceName,
    Instant sampledAt,
    Instant recordedAt
) {}
