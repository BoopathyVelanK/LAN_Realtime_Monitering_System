package com.securesoc.dto.monitoring;

import java.time.Instant;

/** Mirrors the "network" half of collector.NetworkUsageTracker.sample():
 * {"bytesSent": ..., "bytesReceived": ..., "interfaceName": null,
 * "sampledAt": ...}. sampledAt is the agent's own collection timestamp -
 * distinct from NetworkUsageEvent.recordedAt, which the backend generates
 * at ingestion time. May be null from an agent build that predates this
 * field (including anything already sitting in an offline queue) - see
 * MonitoringService.recordNetworkUsage for how that's handled. */
public record NetworkUsageEventRequest(
    Long bytesSent,
    Long bytesReceived,
    String interfaceName,
    Instant sampledAt
) {}
