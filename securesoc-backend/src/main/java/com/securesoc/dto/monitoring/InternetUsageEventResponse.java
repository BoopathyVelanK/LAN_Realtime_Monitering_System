package com.securesoc.dto.monitoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read shape for GET /monitoring/internet-usage - mirrors
 * InternetUsageEvent plus the endpoint's hostname. */
public record InternetUsageEventResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    BigDecimal uploadMb,
    BigDecimal downloadMb,
    Integer periodSeconds,
    Instant recordedAt
) {}
