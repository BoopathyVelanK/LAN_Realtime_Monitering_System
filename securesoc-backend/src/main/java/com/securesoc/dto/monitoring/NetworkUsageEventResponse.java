package com.securesoc.dto.monitoring;

import java.time.Instant;
import java.util.UUID;

/** Read shape for GET /monitoring/network-usage - mirrors
 * NetworkUsageEvent plus the endpoint's hostname. */
public record NetworkUsageEventResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    Long bytesSent,
    Long bytesReceived,
    String interfaceName,
    Instant recordedAt
) {}
