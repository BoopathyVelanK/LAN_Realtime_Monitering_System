package com.securesoc.dto.monitoring;

import java.math.BigDecimal;
import java.time.Instant;

/** Mirrors the "internet" half of collector.NetworkUsageTracker.sample():
 * {"uploadMb": ..., "downloadMb": ..., "periodSeconds": ..., "sampledAt":
 * ...}. sampledAt is the agent's own collection timestamp - distinct from
 * InternetUsageEvent.recordedAt, which the backend generates at ingestion
 * time. May be null from an agent build that predates this field
 * (including anything already sitting in an offline queue) - see
 * MonitoringService.recordInternetUsage for how that's handled. */
public record InternetUsageEventRequest(
    BigDecimal uploadMb,
    BigDecimal downloadMb,
    Integer periodSeconds,
    Instant sampledAt
) {}
