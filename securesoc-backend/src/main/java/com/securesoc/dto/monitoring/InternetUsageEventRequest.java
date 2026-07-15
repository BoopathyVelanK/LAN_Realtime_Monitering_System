package com.securesoc.dto.monitoring;

import java.math.BigDecimal;

/** Mirrors the "internet" half of collector.NetworkUsageTracker.sample():
 * {"uploadMb": ..., "downloadMb": ..., "periodSeconds": ...}. */
public record InternetUsageEventRequest(
    BigDecimal uploadMb,
    BigDecimal downloadMb,
    Integer periodSeconds
) {}
