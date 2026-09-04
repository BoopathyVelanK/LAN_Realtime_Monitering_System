package com.securesoc.dto.monitoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read shape for GET /monitoring/internet-usage - mirrors
 * InternetUsageEvent plus the endpoint's hostname. sampledAt is the
 * agent's collection time (may be null for rows recorded before this
 * field existed, or from a not-yet-upgraded agent); recordedAt remains
 * the backend's own ingestion time, unchanged. */
public record InternetUsageEventResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    BigDecimal uploadMb,
    BigDecimal downloadMb,
    Integer periodSeconds,
    Instant sampledAt,
    Instant recordedAt
) {}
