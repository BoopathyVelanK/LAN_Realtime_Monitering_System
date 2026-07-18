package com.securesoc.dto.monitoring;

import java.time.Instant;
import java.util.UUID;

/** Read shape for GET /monitoring/vpn - mirrors VpnEvent plus the
 * endpoint's hostname. */
public record VpnEventResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    String adapterName,
    boolean active,
    Instant detectedAt
) {}
