package com.securesoc.dto.monitoring;

/** Mirrors the "network" half of collector.NetworkUsageTracker.sample():
 * {"bytesSent": ..., "bytesReceived": ..., "interfaceName": null}. */
public record NetworkUsageEventRequest(
    Long bytesSent,
    Long bytesReceived,
    String interfaceName
) {}
