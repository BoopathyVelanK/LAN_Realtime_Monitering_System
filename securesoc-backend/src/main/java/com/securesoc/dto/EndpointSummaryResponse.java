package com.securesoc.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Field names and shape are a deliberate 1:1 mirror of the frontend's
 * EndpointSummaryResponse interface (frontend/src/types/api.ts).
 */
public record EndpointSummaryResponse(
    UUID id,
    String hostname,
    String macAddress,
    String ipAddress,
    UUID labId,
    String labName,
    String status,
    Instant lastHeartbeatAt,
    String osName,
    String osVersion,
    String cpuInfo,
    Integer ramMb,
    Integer diskGb,
    String agentVersion
) {}
